// THIS FILE WAS GENERATED BY JSON2JAVA
// CHANGES MADE:
//   * created public String getId()
//   * change type from NotificationAccount (deleted) to CredentialAccount

package edu.sjsu.moth.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import edu.sjsu.moth.server.db.Account;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "id_", "type", "created_at", "account", "status", "report" })
public class Notification {

    @JsonProperty("id_")
    public String id;
    @JsonProperty("type")
    public String type;
    @JsonProperty("created_at")
    public String createdAt;
    @JsonProperty("account")
    public Account notificationAccount;
    @JsonProperty("status")
    public Status status;
    @JsonProperty("report")
    public Report report;

    /**
     * No args constructor for use in serialization
     */
    public Notification() {
    }

    public Notification(String id, String type, String createdAt, Account notificationAccount, Status status,
                        Report report) {
        super();
        this.id = id;
        this.type = type;
        this.createdAt = createdAt;
        this.notificationAccount = notificationAccount;
        this.status = status;
        this.report = report;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Notification.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this)))
                .append('[');
        sb.append("id");
        sb.append('=');
        sb.append(((this.id == null) ? "<null>" : this.id));
        sb.append(',');
        sb.append("type");
        sb.append('=');
        sb.append(((this.type == null) ? "<null>" : this.type));
        sb.append(',');
        sb.append("createdAt");
        sb.append('=');
        sb.append(((this.createdAt == null) ? "<null>" : this.createdAt));
        sb.append(',');
        sb.append("account");
        sb.append('=');
        sb.append(((this.notificationAccount == null) ? "<null>" : this.notificationAccount));
        sb.append(',');
        sb.append("status");
        sb.append('=');
        sb.append(((this.status == null) ? "<null>" : this.status));
        sb.append(',');
        sb.append("report");
        sb.append('=');
        sb.append(((this.report == null) ? "<null>" : this.report));
        sb.append(',');
        if (sb.charAt((sb.length() - 1)) == ',') {
            sb.setCharAt((sb.length() - 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = ((result * 31) + ((this.createdAt == null) ? 0 : this.createdAt.hashCode()));
        result = ((result * 31) + ((this.report == null) ? 0 : this.report.hashCode()));
        result = ((result * 31) + ((this.id == null) ? 0 : this.id.hashCode()));
        result = ((result * 31) + ((this.type == null) ? 0 : this.type.hashCode()));
        result = ((result * 31) + ((this.notificationAccount == null) ? 0 : this.notificationAccount.hashCode()));
        result = ((result * 31) + ((this.status == null) ? 0 : this.status.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Notification) == false) {
            return false;
        }
        Notification rhs = ((Notification) other);
        return (((((((this.createdAt == rhs.createdAt) ||
                ((this.createdAt != null) && this.createdAt.equals(rhs.createdAt))) &&
                ((this.report == rhs.report) || ((this.report != null) && this.report.equals(rhs.report)))) &&
                ((this.id == rhs.id) || ((this.id != null) && this.id.equals(rhs.id)))) &&
                ((this.type == rhs.type) || ((this.type != null) && this.type.equals(rhs.type)))) &&
                ((this.notificationAccount == rhs.notificationAccount) || ((this.notificationAccount != null) &&
                        this.notificationAccount.equals(rhs.notificationAccount)))) &&
                ((this.status == rhs.status) || ((this.status != null) && this.status.equals(rhs.status))));
    }

    public String getId() {
        return id;
    }
}