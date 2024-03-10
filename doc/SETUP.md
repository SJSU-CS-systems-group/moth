when you run `mvn package` you will get a `moth-server.jar` file that you can run
with `java -jar moth-server.jar _cfg_`. the `_cfg_` is a simple java properties file that has the server configuration.
**DO NOT PUT THIS FILE IN THE SOURCE DIRECTORY, AND DO NOT CHECK IT IN!!!!**.

here is an example config file:

    server.port=3333
    # moth.example.com will be the hostname to use to access your server
    server.name=moth.example.com
    # mongodb.example.com is the hostname of your mongodb server
    db=mongodb.example.com
    account=tooth
    #spring.debug=true
    #spring.trace=true
    smtp.server=smart_host:port # this should be an SMTP server that can relay emails like 172.27.16.1:2525
    smtp.localPort=2525

