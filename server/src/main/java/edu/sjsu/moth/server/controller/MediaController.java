package edu.sjsu.moth.server.controller;

import edu.sjsu.moth.generated.MediaAttachment;
import edu.sjsu.moth.server.service.MediaService;
import edu.sjsu.moth.server.util.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.PartEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
public class MediaController {

    @Autowired
    MediaService mediaService;

    private Mono<ResponseEntity<Flux<DataBuffer>>> generateResponseFromGridFSResource(MediaService.GridFSStream gfs) {
        return Mono.just(ResponseEntity.ok()
                                 .contentType(MediaType.parseMediaType(
                                         gfs.file().getMetadata().get("_contentType").toString()))
                                 .contentLength(gfs.file().getLength())
                                 .body(gfs.fdb()));
    }

    @GetMapping("/media/attachments/uploads/{id}")
    Mono<ResponseEntity<Flux<DataBuffer>>> getMediaAttachment(@PathVariable long id) {
        return mediaService.getMongoFileInputStreamResource(mediaService.uploadFileNameFromId(id))
                .flatMap(this::generateResponseFromGridFSResource);
    }

    @GetMapping("/media/attachments/uploads/{id}/preview")
    Mono<ResponseEntity<Flux<DataBuffer>>> getMediaAttachmentPreview(@PathVariable long id) {
        return mediaService.getMongoFileInputStreamResource(mediaService.uploadPreviewNameFromId(id))
                .flatMap(this::generateResponseFromGridFSResource);
    }

    @PostMapping(value = "/api/v2/media", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    Mono<ResponseEntity<MediaAttachment>> postApiV2Media(Principal user, @RequestBody Flux<PartEvent> allPartsEvents) {
        var id = Util.generateUniqueId();
        var fileName = mediaService.uploadFileNameFromId(id);
        var filePreviewName = mediaService.uploadPreviewNameFromId(id);

        // this is super tricky since everything comes in in pieces. it's more convenient to use @RequestParts
        // but that causes the creation of temporary files on upload. instead we will reconstruct the structures
        // ourselves. ctx keeps track of what we glean and the bytes that are getting uploaded.
        // NOTE: we will have at most one file in memory at a time.
        var ctx = new UploadContext();
        return allPartsEvents.flatMap(pe -> {
            switch (pe.name()) {
                case "description" -> ctx.description = ctx.description;
                case "focus" -> {
                    var parts = pe.content().toString(UTF_8).split(",");
                    ctx.meta.put("focus", Map.of("x", Integer.parseInt(parts[0]), "y", Integer.parseInt(parts[1])));
                }
                case "file" -> {
                    return processFileUpload(ctx, "original", fileName, pe);
                }
                case "thumbnail" -> {
                    return processFileUpload(ctx, "small", filePreviewName, pe);
                }
            }
            return Mono.empty();
        }).collectList().flatMap(m -> {
            if (!m.contains("original")) return Mono.error(new IOException("no file was uploaded"));
            var attachment = new FillableMediaAttachment(id, fileName, m.contains("small") ? filePreviewName : "",
                                                         ctx.meta, ctx.description, ctx.type);
            // this is a hack that takes advantage of the hope that an attachment upload will proceed a status
            // post within a few minutes. we need to map the media id to the MediaAttachments
            // TODO: create a media attachments collection
            mediaService.cacheAttachment(Long.toString(id), attachment);
            return Mono.just(ResponseEntity.ok(attachment));
        });
    }

    // we use the same logic for the file and preview, the only differences are the file name the key that we are going
    // to use to store the meta data about the file in the media attachment (tracked in ctx)
    private Mono<String> processFileUpload(UploadContext ctx, String metaKey, String fileName, PartEvent pe) {
        try {
            if (ctx.bytesInProcess == null) {
                // if bytesInProcess is null, the is the first part of a new file
                if (pe.headers().getContentLength() > InstanceController.IMAGE_SIZE_LIMIT) {
                    return Mono.error(
                            new RuntimeException("Upload bigger than " + InstanceController.IMAGE_SIZE_LIMIT));
                }
                // we checked the limit earlier so we know we can cast to an int
                ctx.bytesInProcess = new byte[(int) pe.headers().getContentLength()];
                ctx.inProcessOffset = 0;
            }
            int count = pe.content().readableByteCount();
            pe.content().read(ctx.bytesInProcess, ctx.inProcessOffset, count);
            ctx.inProcessOffset += count;
            if (pe.isLast()) {
                if (pe.content().readableByteCount() > InstanceController.IMAGE_SIZE_LIMIT) {
                    return Mono.error(new RuntimeException("image size larger than %dM".formatted(
                            InstanceController.IMAGE_SIZE_LIMIT / InstanceController.MEG)));
                }
                ctx.meta.put(metaKey, processContent(ctx.bytesInProcess, pe.headers().getContentType()));
                ctx.type = pe.headers().getContentType().getType();
                ctx.contentType = pe.headers().getContentType();

                var db = new DefaultDataBufferFactory().wrap(ctx.bytesInProcess);
                // reset for the next upload
                ctx.bytesInProcess = null;
                return mediaService.storeMedia(ctx.contentType.toString(), metaKey, fileName, db);
            }
        } catch (IOException e) {
            return Mono.error(e);
        }
        // not last, so there is more to come!
        return Mono.empty();
    }

    // extracts key meta-data for the media
    // see https://docs.joinmastodon.org/entities/MediaAttachment/
    private Map<String, Object> processContent(byte[] content, MediaType contentType) throws IOException {
        if ("image".equals(contentType.getType())) {
            var image = ImageIO.read(new ByteArrayInputStream(content));
            if (image != null) {
                if (image.getHeight() * image.getHeight() > InstanceController.IMAGE_MATRIX_LIMIT)
                    throw new RuntimeException("image matrix larger than %dM".formatted(
                            InstanceController.IMAGE_MATRIX_LIMIT / InstanceController.MEG));
                return Map.of("width", image.getWidth(), "height", image.getHeight(), "size",
                              image.getWidth() + "x" + image.getHeight(), "aspect",
                              (float) image.getWidth() / (float) image.getHeight());
            }
        }
        throw new RuntimeException("Cannot handle content: " + contentType);
    }

    static class UploadContext {
        public MediaType contentType;
        String description;
        /**
         * the first part of the MIME content type: image, video, audio (gifv is an outlier and not yet supported)
         */
        String type;
        byte[] bytesInProcess;
        int inProcessOffset;
        Map<String, Object> meta = new HashMap<>();
    }

    class FillableMediaAttachment extends MediaAttachment {
        FillableMediaAttachment(long id, String fileName, String previewFileName, Map<String, Object> meta,
                                String description, String type) {
            this.id = Long.toString(id);
            this.url = MothController.BASE_URL + fileName;
            this.previewUrl = meta.containsKey("small") ? MothController.BASE_URL + previewFileName : url;
            this.meta = meta;
            this.description = description == null ? "" : description;
            this.blurhash = "";
            this.type = type;
        }

        public String toString() {
            return "FillableMediaAttachment: " + id + " " + url;
        }
    }
}
