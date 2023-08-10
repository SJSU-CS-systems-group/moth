package edu.sjsu.moth.server.db;

import edu.sjsu.moth.generated.Marker;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Document("timeline")
public class TimelineRecord {
    @Id
    public String acct;
    public Map<String, Marker> markers;
}
