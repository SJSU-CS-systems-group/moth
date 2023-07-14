# MAC OS X / LINUX INSTALLATION:

_Full Documentation at:_ https://www.mongodb.com/docs/manual/tutorial/install-mongodb-on-os-x/


You’ll need to install Homebrew if you haven’t done so already – Homebrew is a third party package manager for Linux and Mac OS that helps install open source CLI software (further reading on installation: https://brew.sh/#install )


You just need the below command. It will guide you through installation once you run it in Terminal.

`/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"`

After you have it installed, run:

`brew tap mongodb/brew`


Then:

`brew install mongodb-community@6.0`


_** (OPTIONAL) **_ You can run:
`
brew -prefix`


to see where any relevant files or directories created via Homebrew are located.




Although there are alternative methods, it’s recommended to start MongoDB via:

`brew services start mongodb-community@6.0`



For ease of use, install Compass, a MongoDB GUI.

_Download Link for Compass:_ https://www.mongodb.com/try/download/compass

Once you start up Compass, it should automatically set-up to connect to port 27017 of the localhost – once you hit Connect, you’ll have access to all of your collections.

To stop MongoDB:

`brew services stop mongodb-community@6.0`

## **Troubleshooting:**

If you run into errors trying to start it up, it’s usually because (1) you are trying to start a MongoDB process, when one is already in session (this becomes an issue because it usually tries to access Port 27017 as mentioned above)

or, (2) your DB files are in an inaccessible place (where your System’s user doesn’t have read/write permissions).

To troubleshoot (1) try running:

`ps ax | grep mongodb`


The output will look like:

```
57093 s999  S      0:01.75 /user/local/opt/mongodb-community/bin/mongod --config /user/local/etc/mongod.conf
57112 s000  S+     0:00.00 grep mongod 
```

This command shows all running processes on the system, with the leftmost-column being the Process ID (PID). In my case, I have one session running and it has already been running for 0:01.75, or 1.75 minutes. You can see the second process below it – this is just the grep command searching so you can ignore it.

You can run:

`kill [PID]`


To kill the process before starting it again.

If you followed the above, (2) shouldn’t happen, but if it does, you can create a new folder and start MongoDB from there via:

`mongod --dbpath /filePath/to/newFolder`


And after it starts, you should be fine to use Compass as discussed above.




