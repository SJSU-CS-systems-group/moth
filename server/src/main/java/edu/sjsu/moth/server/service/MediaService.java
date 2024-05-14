package edu.sjsu.moth.server.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import edu.sjsu.moth.generated.MediaAttachment;
import edu.sjsu.moth.server.util.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.ReactiveGridFsTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

@Configuration
public class MediaService {
    @Autowired
    ReactiveGridFsTemplate mongoStorage;

    Util.TTLHashMap<String, MediaAttachment> idToMedia = new Util.TTLHashMap<>(1, TimeUnit.HOURS);

    public String uploadFileNameFromId(long id) {
        return "/media/attachments/uploads/%d".formatted(id);
    }

    public String uploadPreviewNameFromId(long id) {
        return "/media/attachments/uploads/%d/preview".formatted(id);
    }

    public Mono<GridFSStream> getMongoFileInputStreamResource(String filename) {
        return mongoStorage.findOne(new Query(Criteria.where("filename").is(filename))).flatMap(
                f -> mongoStorage.getResource(f).map(r -> r.getDownloadStream(8192))
                        .map(fda -> new GridFSStream(f, fda)));
    }

    public Mono<String> storeMedia(String contentType, String metaKey, String fileName, DefaultDataBuffer db) {
        return mongoStorage.store(Mono.just(db), fileName, contentType).map(oid -> metaKey);
    }

    public void cacheAttachment(String id, MediaAttachment attachment) {
        idToMedia.put(id, attachment);
    }

    public MediaAttachment lookupCachedAttachment(String id) {
        return idToMedia.get(id);
    }

    public record GridFSStream(GridFSFile file, Flux<DataBuffer> fdb) {}
}
