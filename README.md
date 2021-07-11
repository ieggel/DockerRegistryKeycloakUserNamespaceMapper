# Keycloak docker registry user namespace mapper

_Docker Registry V2 Authentciation is an OIDC-Like protocol used to authenticate users against a Docker registry. Keycloak’s implementation of this protocol allows for a Keycloak authentication server to be used by a Docker client to authenticate against a registry._ 

Keycloak already comes with the _AllowAllDockerProtocolMapper_ which populates the token that is issued by Keycloak for the Docker registry with the requested scope. In practice this means that if the user successfully authenticates they have full push/pull permissions to any repository in the registry. In most cases this is not enough.

This project provides a custom role- and namespace-based mapper which populates the token with the requested scope if:

- user has **admin role** assigned.
- user has **user role** assigned and requested resource consists of a 1-level **namespace** (i.e. \<namespace>/\<imagename>) that matches his username.
 
If more scopes are present than what has been requested, they will be removed.

## Dockerfile Setup

The Dockerfile supplied in this repository contains the instructions required to build the module and bundle it with the official jboss/keycloak image. This command will build the extended image:

```
docker build . -t keycloak-extended
```

In addition to supporting the user namespace module, the resulting image also has docker support option enabled by default (that is, no `-Dkeycloak.profile.feature.docker=enabled` argument is needed).

## Manual Setup

### 1. Generate jar file

`./gradlew clean jar`

This will create a jar named _docker-user-namespace-mapper.jar_ in _build/libs/_.

### 2. (Only if using Keycloak in docker container: Copy jar to container)

In case you run Keycloak as a Docker container, first copy the jar file to the container:

`docker cp build/libs/docker-user-namespace-mapper.jar <CONTAINER>:/docker-user-namespace-mapper.jar`


### 3. Create module

You first have to create a module:

`KEYCLOAK_HOME/bin/jboss-cli.sh --command="module add --name=ch.hevs.medgift.keycloak.docker-user-namespace-mapper --resources=/docker-user-namespace-mapper.jar --dependencies=org.keycloak.keycloak-core,org.keycloak.keycloak-services,org.keycloak.keycloak-server-spi-private,org.keycloak.keycloak-server-spi"`


### 4. Register module

You have two possibilities in order to register a module. I recommend the second approach.

#### 4.1 Register module by editing config file

Once you’ve created the module you need to register this module with Keycloak. This is done by editing the keycloak-server subsystem section of standalone.xml, standalone-ha.xml, or domain.xml, and adding it to the providers:

```XML
<subsystem xmlns="urn:jboss:domain:keycloak-server:1.1">
    <web-context>auth</web-context>
    <providers>
        <provider>module:ch.hevs.medgift.keycloak.docker-user-namespace-mapper</provider>
    </providers>
    ...
```

*Note*: _Keep in mind that if you use the official [jboss/keycloak](https://hub.docker.com/r/jboss/keycloak/) Docker image with the default configuration, you have to edit standalone-ha.xml and NOT standalone.xml. More info here: [https://stackoverflow.com/questions/57208709/keycloak-spi-providers-and-layers-not-loading-when-using-docker](https://stackoverflow.com/questions/57208709/keycloak-spi-providers-and-layers-not-loading-when-using-docker)_.

#### 4.2 Register module using the jboss-cli

Alternatively - instead of manually editing _standalone.xml_, _standalone-ha.xml_, or _domain.xml_ - you can execute the following command:

`KEYCLOAK_HOME/bin/jboss-cli.sh --connect --command="/subsystem=keycloak-server:list-add(name=providers, value=module:ch.hevs.medgift.keycloak.docker-user-namespace-mapper)"`

This will automatically edit the correct file which is - depending on your config (e.g. cluster mode) - currently used by Keycloak.

### 5. Start/restart Keycloak
