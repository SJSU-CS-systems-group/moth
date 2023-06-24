package edu.sjsu.moth.server;

public class Instance {
    private String domain;
    private String title;
    private String version;
    private String sourceUrl;
    private String description;
    private Usage usage;
    private Thumbnail thumbnail;
    private String[] languages;
    private Configuration configuration;
    private Registrations registrations;
    private Contact contact;
    private Rule[] rules;

    public Instance(String domain, String title, String version, String sourceUrl, String description,
                    Usage usage, Thumbnail thumbnail, String[] languages, Configuration configuration,
                    Registrations registrations, Contact contact, Rule[] rules) {
        this.domain = domain;
        this.title = title;
        this.version = version;
        this.sourceUrl = sourceUrl;
        this.description = description;
        this.usage = usage;
        this.thumbnail = thumbnail;
        this.languages = languages;
        this.configuration = configuration;
        this.registrations = registrations;
        this.contact = contact;
        this.rules = rules;
    }

    //getters and setters
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public Thumbnail getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(Thumbnail thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String[] getLanguages() {
        return languages;
    }

    public void setLanguages(String[] languages) {
        this.languages = languages;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public Registrations getRegistrations() {
        return registrations;
    }

    public void setRegistrations(Registrations registrations) {
        this.registrations = registrations;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public Rule[] getRules() {
        return rules;
    }

    public void setRules(Rule[] rules) {
        this.rules = rules;
    }

    //sub classes
    public static class Usage {
        private int posts;
        private int users;

        public Usage(int posts, int users) {
            this.posts = posts;
            this.users = users;
        }

        //getter and setters
        public int getPosts() {
            return posts;
        }

        public void setPosts(int posts) {
            this.posts = posts;
        }

        public int getUsers() {
            return users;
        }

        public void setUsers(int users) {
            this.users = users;
        }
    }

    public static class Thumbnail {
        private String url;
        private int width;
        private int height;

        public Thumbnail(String url, int width, int height) {
            this.url = url;
            this.width = width;
            this.height = height;
        }

        //getter and setters!
        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }
    }

    public static class Configuration {
        private boolean supportsStreaming;

        public Configuration(boolean supportsStreaming) {
            this.supportsStreaming = supportsStreaming;
        }

        //getter and setters
        public boolean isSupportsStreaming() {
            return supportsStreaming;
        }

        public void setSupportsStreaming(boolean supportsStreaming) {
            this.supportsStreaming = supportsStreaming;
        }
    }

    public static class Registrations {
        private boolean open;

        public Registrations(boolean open) {
            this.open = open;
        }

        //getter and setters
        public boolean isOpen() {
            return open;
        }

        public void setOpen(boolean open) {
            this.open = open;
        }
    }

    public static class Contact {
        private String email;
        private String phoneNumber;

        public Contact(String email, String phoneNumber) {
            this.email = email;
            this.phoneNumber = phoneNumber;
        }

        //getter and setter
        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }
    }

    public static class Rule {
        private String id;
        private String text;

        public Rule(String id, String text) {
            this.id = id;
            this.text = text;
        }

        //getter and setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
