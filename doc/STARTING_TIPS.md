# guiding principles

1. write as little code as possible: code comes with a maintenance burden and surface area for bugs. try to write
   concise code using records and avoid getters/setters unless you need to override some behavior.
2. use json2java in the bin directory to generate java classes from JSON. it will help avoid typos in JSON names.
   unfortunately, json2java isn't perfect, so you may need to edit the generated code. the most common motivation for
   edits is to fix references to existing classes.
3. in the server source directory structure: **controller** contains Controller endpoints. **db** contains mongodb
   repository classes and record classes, there shouldn't be any controllers or logic there. **service** contains logic
   to update the database and implement policies. **util** contains miscellaneous utility functions.
4. when naming controller functions, base it on the endpoints. for example /api/v1/follow_requests should be named
   followRequests() and /api/v2/follow_requests should be named followRequestsV2().
5. pick obvious names for variables. in general, intellij suggests good ones. long names are okay. the exceptions are
   lambda parameters that are usually one or two parameters.
5. let IntelliJ do the formatting for you. we have project specific formatting rules, so you don't need to worry about
   the formatting. i've found it useful to go to Settings -> Tools -> Actions on Save and select "Reformat", "
   Optimize", "Rearrange", and "Run code cleanup". that way every time i save the file (^S) it will reformat everything.
6. reformatting can help a lot (!!!) when doing mono chains. i had the following chain that had a bug that was
   impossible to find. the chain was:

        return accountService.getAccount(request.username)
                .map(a -> Mono.error(ERR_TAKEN)
                .then(accountService.createAccount(request.username, request.password))
                .then(generateAccessToken(request.username, appName, appWebsite))
                .map(token -> ResponseEntity.ok(new TokenResponse(token.token, "*"))));

   the compiler was complaining about the return type. when i reformatted the file i saw:

        return accountService.getAccount(request.username)
                .map(a -> Mono.error(ERR_TAKEN)
                        .then(accountService.createAccount(request.username, request.password))
                        .then(generateAccessToken(request.username, appName, appWebsite))
                        .map(token -> ResponseEntity.ok(new TokenResponse(token.token, "*"))));

   my parenthesis was wrong, and the reformatting shows that the .then is attaching to the error inside the map rather
   than the map itself.
9. try to do small pull requests. it makes reviewing easier and keeps things a little more stable.
7. work on things that are broken. if you can identify something that isn't working, it's easy to figure out what you
   are supposed to do and verify that you have implemented the function.
8. unit tests are awesome! they can ensure that something you've fixed stays fixed.