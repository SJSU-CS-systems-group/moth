package edu.sjsu.moth.server.db;

import com.querydsl.core.annotations.QueryEntity;
import edu.sjsu.moth.generated.Status;
import edu.sjsu.moth.generated.StatusEdit;
import nonapi.io.github.classgraph.json.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;

@QueryEntity
@Document
public class StatusEditCollection {
    @Id
    public String id;
    public ArrayList<StatusEdit> collection;

    public StatusEditCollection() {
        this.collection = new ArrayList<>();
    }

    public StatusEditCollection(String id) {
        this.id = id;
        this.collection = new ArrayList<>();
    }

    public StatusEditCollection(String id, ArrayList<StatusEdit> collection) {
        this.id = id;
        this.collection = collection;
    }

    public StatusEditCollection addEdit(Status s) {
        StatusEdit edit = new StatusEdit(s.content, s.spoilerText, s.sensitive, s.createdAt, s.account, s.mediaAttachments, s.emojis);
        collection.add(edit);
        return this;
    }

}
