# Bootiful Redis 


* general appliance that serves lots of use cases 
- ubiquitous client libraries (even more so because it can be made to speak the memcache protocol)
- has a lot of features that are fairly obscure
- easily deployed on lots of platforms cloud or otherwise 
- redislabs leads its development. the also provide the cloud foundry integrations on PWS / PCF 
- they also provide an RDBMS 
- spring boot autoconfigures things 
- and the spring data module is one of theoldest in the spring data portfolio. been around since very near the beginning. its also evolved nicely. its now also a reactive option.
- build a new project on the initialzr, choose sesion, data redis, thymelead, web, and manually add commons-pool2
- the template is central to Spring's Data access story. it has lots of subsystems (geography) 
- did u know it also supports repositories? 
- pub/sub
- and its cleanly integrted into the spring cache manager abstraction?
- and it can handle external sessions? 
- its also got a reactive variant, and that variant is the one u see in play w/ the spring cloud gateway. its in turn using atomic numbers.
- next steps: pipelining (batching), the redis labs clustering and RDBMS, the spring cloudd stream binder, and so much more. 

- 
