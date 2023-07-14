# WINDOWS INSTALLATION:
_Download:_ https://www.mongodb.com/try/download/community

_Select Platform: Windows, then Package: .msi when downloading_

Run the .msi file and choose Complete Setup,
then check the 'Install MongoD as a Service' box,
and 'Run the service as a Network Service user'.

The service name can be whatever you want, and the Data Directory/Log Directory can be any file-path that you want.

Make sure to make note of where your Data Directory is -- you might need the file-path later on.

Since we installed it as a service, you can go to the Services console and run/stop it directly from there!
(you can search for it and open it from the Services app)

_Download Link for Compass:_ https://www.mongodb.com/try/download/compass

Once you start up Compass, it should automatically set-up to connect to port 27017 of the localhost – once you hit Connect, you’ll have access to all of your collections.
