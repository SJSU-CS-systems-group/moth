rather than creating Java classes to represent JSON messages by hand,
please use json2java.sh found in the bin directory.

to use it follow these steps (you should do this in a temporary directory):

1. download the JSON you would like to create java classes for. (for
   example: `url -H "Accept: application/activity+json" https://don.homeofcode.com/api/v1/instance  > instance`)
   **NOTE:** the name of the first class will be based on the name of that file.
2. run json2java.sh on that file. you may need to specify the full path.
   (for example: `~/git/moth/bin/json2java.sh instance`.)
3. make sure there aren't any changes to existing files that will break something. if there are you, will need to rename
   the classes under staging to have a version number, like ConfigV2. make sure you rename the class and declarations of
   the class under staging.

**if you make changes to a generated file, update the comment at the top of the file to indicate that so that your
changes aren't later overwritten!**