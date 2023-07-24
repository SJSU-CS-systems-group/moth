
// THIS FILE WAS GENERATED BY JSON2JAVA
// IT HAS NOT BEEN CHANGED. (IF IT HAS REMOVE THIS LINE)
// CHANGES MADE:
//   * NONE SO FAR


package edu.sjsu.moth.generated;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "action_taken",
    "action_taken_at",
    "category",
    "comment",
    "forwarded",
    "created_at",
    "status_ids",
    "rule_ids",
    "target_account"
})
public class Report {

    @JsonProperty("id")
    public String id;
    @JsonProperty("action_taken")
    public Boolean actionTaken;
    @JsonProperty("action_taken_at")
    public Object actionTakenAt;
    @JsonProperty("category")
    public String category;
    @JsonProperty("comment")
    public String comment;
    @JsonProperty("forwarded")
    public Boolean forwarded;
    @JsonProperty("created_at")
    public String createdAt;
    @JsonProperty("status_ids")
    public List<String> statusIds = new ArrayList<String>();
    @JsonProperty("rule_ids")
    public Object ruleIds;
    @JsonProperty("target_account")
    public TargetAccount targetAccount;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Report() {
    }

    public Report(String id, Boolean actionTaken, Object actionTakenAt, String category, String comment, Boolean forwarded, String createdAt, List<String> statusIds, Object ruleIds, TargetAccount targetAccount) {
        super();
        this.id = id;
        this.actionTaken = actionTaken;
        this.actionTakenAt = actionTakenAt;
        this.category = category;
        this.comment = comment;
        this.forwarded = forwarded;
        this.createdAt = createdAt;
        this.statusIds = statusIds;
        this.ruleIds = ruleIds;
        this.targetAccount = targetAccount;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Report.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("id");
        sb.append('=');
        sb.append(((this.id == null)?"<null>":this.id));
        sb.append(',');
        sb.append("actionTaken");
        sb.append('=');
        sb.append(((this.actionTaken == null)?"<null>":this.actionTaken));
        sb.append(',');
        sb.append("actionTakenAt");
        sb.append('=');
        sb.append(((this.actionTakenAt == null)?"<null>":this.actionTakenAt));
        sb.append(',');
        sb.append("category");
        sb.append('=');
        sb.append(((this.category == null)?"<null>":this.category));
        sb.append(',');
        sb.append("comment");
        sb.append('=');
        sb.append(((this.comment == null)?"<null>":this.comment));
        sb.append(',');
        sb.append("forwarded");
        sb.append('=');
        sb.append(((this.forwarded == null)?"<null>":this.forwarded));
        sb.append(',');
        sb.append("createdAt");
        sb.append('=');
        sb.append(((this.createdAt == null)?"<null>":this.createdAt));
        sb.append(',');
        sb.append("statusIds");
        sb.append('=');
        sb.append(((this.statusIds == null)?"<null>":this.statusIds));
        sb.append(',');
        sb.append("ruleIds");
        sb.append('=');
        sb.append(((this.ruleIds == null)?"<null>":this.ruleIds));
        sb.append(',');
        sb.append("targetAccount");
        sb.append('=');
        sb.append(((this.targetAccount == null)?"<null>":this.targetAccount));
        sb.append(',');
        if (sb.charAt((sb.length()- 1)) == ',') {
            sb.setCharAt((sb.length()- 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result* 31)+((this.createdAt == null)? 0 :this.createdAt.hashCode()));
        result = ((result* 31)+((this.ruleIds == null)? 0 :this.ruleIds.hashCode()));
        result = ((result* 31)+((this.actionTaken == null)? 0 :this.actionTaken.hashCode()));
        result = ((result* 31)+((this.comment == null)? 0 :this.comment.hashCode()));
        result = ((result* 31)+((this.targetAccount == null)? 0 :this.targetAccount.hashCode()));
        result = ((result* 31)+((this.id == null)? 0 :this.id.hashCode()));
        result = ((result* 31)+((this.category == null)? 0 :this.category.hashCode()));
        result = ((result* 31)+((this.statusIds == null)? 0 :this.statusIds.hashCode()));
        result = ((result* 31)+((this.forwarded == null)? 0 :this.forwarded.hashCode()));
        result = ((result* 31)+((this.actionTakenAt == null)? 0 :this.actionTakenAt.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Report) == false) {
            return false;
        }
        Report rhs = ((Report) other);
        return (((((((((((this.createdAt == rhs.createdAt)||((this.createdAt!= null)&&this.createdAt.equals(rhs.createdAt)))&&((this.ruleIds == rhs.ruleIds)||((this.ruleIds!= null)&&this.ruleIds.equals(rhs.ruleIds))))&&((this.actionTaken == rhs.actionTaken)||((this.actionTaken!= null)&&this.actionTaken.equals(rhs.actionTaken))))&&((this.comment == rhs.comment)||((this.comment!= null)&&this.comment.equals(rhs.comment))))&&((this.targetAccount == rhs.targetAccount)||((this.targetAccount!= null)&&this.targetAccount.equals(rhs.targetAccount))))&&((this.id == rhs.id)||((this.id!= null)&&this.id.equals(rhs.id))))&&((this.category == rhs.category)||((this.category!= null)&&this.category.equals(rhs.category))))&&((this.statusIds == rhs.statusIds)||((this.statusIds!= null)&&this.statusIds.equals(rhs.statusIds))))&&((this.forwarded == rhs.forwarded)||((this.forwarded!= null)&&this.forwarded.equals(rhs.forwarded))))&&((this.actionTakenAt == rhs.actionTakenAt)||((this.actionTakenAt!= null)&&this.actionTakenAt.equals(rhs.actionTakenAt))));
    }

}
