# Signum Node (previously Burstcoin Reference Software)
[![Build](https://github.com/burst-apps-team/burstcoin/actions/workflows/build.yml/badge.svg)](https://github.com/burst-apps-team/burstcoin/actions/workflows/build.yml)
[![GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE.txt)
[![Get Support at https://discord.gg/ms6eagX](https://img.shields.io/badge/join-discord-blue.svg)](https://discord.gg/ms6eagX)

The world's first HDD-mined cryptocurrency using an energy efficient
and fair Proof-of-Commitment (PoC+) consensus algorithm.

The three supported database backends are:

- H2 (embedded, recommended)
- MariaDB (advanced users)
- PostgreSQL (advanced users)

> Requirements: [Docker installed](https://docs.docker.com/engine/install/)


# Running on Docker
Running the Signum node on docker is a great way to provide easy updates upon new releases, but requires some knowledge of how to use docker.

These containers are built with a script that will automatically download the latest version of Phoenix and install it during startup. 

Each time you launch the container, it will download and install the latest Phoenix interface.

The easiest way is to use docker-compose to launch the image consistently every time.


## Database Choices


## Using an optional RDBMS (MariaDB, PostgreSQL)

Signum Node uses an embedded file based database (H2) as default. But it's possible to use either MariaDB or PostgreSQL as alternative database.

----

### Should I use MariaDB, PostgreSQL or H2?

H2 is a very fast file based (embedded) database. Signum Node builds up the entire database out of the box and does not require any further set up.
This makes H2 an interesting choice especially for less-technical users who just want to start and/or run a local (not publicly accessible) node. Choose this, if you want to run just a local node without public exposure and/or
you don't want to connect to the database while running the node. Furthermore, the resulting database file is easily shareable, such others can use a snapshot and sync from there.

MariaDB and PostgreSQL on the other hand require an additional set-up. It is the better choice for publicly accessible nodes,
as they are considered more stable, especially under higher load.

MariaDB and PostgreSQL are not as fast as H2, so expect higher re-synchronisation times.
The performance hit for MariaDB and PostgreSQL is related to the TCP/IP connection, which is per se slower than File-IO (especially for SSDs).
Due to that model concurrent access is possible, i.e. one can run an additional service against the same database, which is not possible with H2, as the file gets locked.

|            | Stability | Speed | Setup | Backup | Concurrency | Purpose                          |
|------------|-----------|------|-------|--------|----------|----------------------------------|
| H2         | ⭐         | ⭐⭐   | ⭐⭐⭐    | ⭐⭐⭐       | ❌         | Local Node                       |  
| MariaDB    | ⭐⭐        | ⭐    | ⭐     | ⭐       |   ✅       | Public Node, Additional Services |  
| PostgreSQL | ⭐⭐        | ⭐    | ⭐     | ⭐       |   ✅       | Public Node, Additional Services |  

## Mariadb Image via docker-compose
Running the Mariadb image is similar to the H2 image, except it does not create a database docker volume. You must also provide the configuration in `node.properties` to point it to a mariadb instance.

1. Run `mkdir signum-docker` or similar to create a folder to house all your docker files.
2. Run `cd signum-docker` then `mkdir conf`
3. Run `touch conf/node.properties`, then edit that file to override the nodes default settings as desired.
4. In `node.properties`, set the connection string and credentials:
```
DB.Url=jdbc:mariadb://mariadb:3306/signum
DB.Username=root
DB.Password=signum
```
5. Run `docker volume create signum_db` to create a location to house your database.

6. Create a file called `docker-compose.yml` with these contents:
```yml
version: "3.8"
services:
  node:
    image: signumnetwork/node:latest-maria
    init: true
    depends_on:
      - mariadb
    stop_grace_period: 5m
    networks:
      - backend
    ports:
      - "8123:8123"
      - "8125:8125"
      - "8126:8126"
    volumes:
      - "./conf:/conf"

  mariadb:
    image: lscr.io/linuxserver/mariadb
    environment:
      - PUID=1000
      - PGID=1000
      - MYSQL_ROOT_PASSWORD=signum
      - TZ=America/New_York
      - MYSQL_DATABASE=signum
      - MYSQL_USER=signum
      - MYSQL_PASSWORD=signum
    volumes:
      - signum_db:/config
    networks:
      - backend

networks:
  backend:

volumes:
  signum_db:
    external: true
```
7. Run `docker-compose up -d` from the directory containing your dockercompose.yml file to start both servers.
8. You may turn the servers off with `docker-compose down`

The MariaDB defaults slow down syncing. See [Performance Tuning](../DB_SETUP_MARIADB.md#performance-tuning) for better settings. With the `linuxserver/mariadb` image, put them in `custom.cnf` in the config volume.

### More detail
The `signumnetwork/node:latest-mariadb` image in the `node` service will create 1 docker volume by default: a `conf` volume, though it will show up in `docker volume ls` as a UUID. The above compose file overrides this volume and maps it to a filesystem location instead.

By default the node will use all default settings. The `volumes` section in the docker-compose.yml file above tells docker to map the internal '/conf' folder to an external folder on the host's hard drive instead of using a docker volume. This allows node configuration files to be easily customized.

The `conf` folder inside the 'signum-docker' folder should contain `node.properties` and several other files. Make any desired changes to these config files.

Finally, the `docker-compose up -d`, while inside the 'signum-docker' folder, will start the node. You can monitor it with `docker-compose logs`.

The above compose file for the `mariadb` service along with the `docker volume create` command overrides the mariadb default database location so that you will have a persistent `signum_db` volume that will exist until you delete it explicitly or run `docker volume prune` while no container exists that is attached to the volume.

Docker-compose can launch multiple replicas if software properly supports it, but the Signum node is not designed with this feature of docker in mind, so this docker-compose file specificies only a single replica.

Once you port-forward the two relevant ports, the website should be available at the server's host name on port 8125. There is no need to expose the mariadb ports. The two images communicate within the docker network.

## H2 Image via docker-compose
> \>>>ATTENTION<<<
>
> H2 is not currently recommended as there is a problem with it disconnecting the database randomly, leaving the node in a stuck state. Please use mariadb.
>
> H2 instructions are provided here only as a reference.
To use the H2 docker image:

1. Run `mkdir signum-docker` or similar to create a folder to house all your docker files.
2. Run `cd signum-docker` then `mkdir conf`
3. Run `touch conf/node.properties`, then edit that file to override the nodes default settings as desired.
4. Run `docker volume create signum_db` to create a location to house your database.
5. Create a file called `docker-compose.yml` with these contents:
```yml
version: "3.8"
services:
  signum:
    image: signumnetwork/node:latest-h2
    init: true
    stop_grace_period: 2m
    deploy:
      replicas: 1
    restart: always
    ports:
      - "8123:8123"
      - "8125:8125"
      - "8126:8126"
    volumes:
      - ./conf:/conf
      - signum_db:/db

volumes:
  signum_db:
    external: true
```
6. Run `docker-compose up -d` from the directory containing your dockercompose.yml file to start both servers.
7. You may turn the servers off with `docker-compose down`

You should now have a running docker node. Port-forward it manually since the docker container interferes with automatic port forwarding. Read on for more details on what's going on.

### More detail
The H2 image will create 2 docker volumes by default: a `db` volume and a `conf` volume, though these will show up in `docker volume ls` as a pair of UUIDs. The above compose file and `docker volume create` command, however, overrides the `db` mapping so that you will have a persistent `signum_db` volume that will exist until you delete it explicitly or run `docker volume prune` while no container exists that is attached to the volume.

By default the node will use all default settings. The `volumes` section in the docker-compose.yml file above tells docker to map the internal '/conf' folder to an external folder on the host's hard drive instead of using a docker volume. This allows node configuration files to be easily customized.

The `conf` folder inside the 'signum-docker' folder should contain `node.properties` and several other files. Make any desired changes to these config files.

Finally, the `docker-compose up -d`, while inside the 'signum-docker' folder, will start the node. You can monitor it with `docker-compose logs`.

The container name is generated from the folder name, followed by the name of the service in the docker-compose file, then a number indicating which replica instance it is. Docker-compose can launch multiple replicas if software properly supports it, but the Signum node is not designed with this feature of docker in mind, so this docker-compose file specificies only a single replica.

Once you port-forward the two relevant ports, the website should be available at the server's host name on port 8125.


## Starting manually in the console
> NOTE: This command uses the H2 version of the node for simplicity.
>
> H2 is not currently recommended as there is a problem with it disconnecting the database randomly, leaving the node in a stuck state. Please use mariadb.
>
> You can run the mariadb version similarly to this if you provide it with a pre-existing mariadb instance.

Alternatively, you may want to run the container manually in the command line. Then you need to link/mount your local `conf` and `db` folders to the container and forward the ports.
The command may look like this:

```bash
docker run -d -p "8123:8123" -p "8125:8125" -v "/home/me/crypto/signum-node/db:/db" -v "/home/me/crypto/signum-node/conf:/conf" signumnetwork/node:latest-h2
```

> using `docker ps` will list the running container 

Be sure that the `db` and `conf` folder exist under the give path. You do not need to put any files there, they will be created on the first startup.
Within the `conf` folder you'll see three file created:

- logging-default.properties - default logging config reference
- logging.properties - override default logging settings
- node-default.properties - default config reference
- node.properties - override default config settings


# Building Image Manually

> requires to have cloned this repository using `git clone --depth 1 https://github.com/signum-network/signum-node.git`

## Build Arguments

The Dockerfile supports the following build arguments

|           | Purpose              | Allowed Values                 | Default |
|-----------|----------------------|--------------------------------|---------|  
| database  | Select the database  | h2, mariadb, postgres          | h2      |  
| network   | Select the network   | mainnet, testnet               | mainnet |  
| port_p2p  | The P2P port         | any port, usually 8123 or 7123 | 8123    |  
| port_http | The HTTP API port    | any port, usually 8125 or 6876 | 8125    |  
| port_ws   | The Websocket port   | any port, usually 8126 or 6877 | 8126    |  

__Examples__:

Build H2 for Testnet
```
docker build --build-arg database=h2 --build-arg network=testnet -t signum/node:h2-testnet .
```

Build PostgreSQL for Mainnet

```
docker build --build-arg database=postgres -t signum/node:postgres .
```

> Note: this setting just defines some preset default configurations. You can always override the configuration in your `node.properties` file

To run the image see [here](#starting-manually-in-the-console)

### Start Script

The provided start script updates Phoenix and Classic wallet each time you restart a docker container!

# Configuring Dockerhub Releases on Github
The github actions workflow that releases docker images to dockerhub is set up to do so automatically whenever there is a 'release' created via the Github interface, or when the workflow is manually triggered. Some configuration is required to succesfully run this workflow.

*Note: This workflow only supports dockerhub.*

1. Set up a [docker account][docker-signup]
2. Open your github repository settings and navigate to the 'Environments' tab on the left
3. Create a new evironment named "dockerhub-publish"
4. Open that environment up and at the bottom of the page click the 'Add Secret' button.
5. Add 3 separate secrets:
    a. `DOCKERHUB_USERNAME` - set the value to the username you created in step 1
    b. `DOCKERHUB_PASSWORD` - set the value to the password you created in step 1
    c. `DOCKERHUB_REPO` - set the value to the desired docker repository path (e.g. `signumnetwork/node`)
6. The file `.github/workflows/dockerhub.yml` (provided) must exist in the github repo's default branch. This is usually `main` or, for legacy repos, `master`. Obviously, the file must also contain all the proper instructions.

With all of that in place, Github Actions should automatically build and deploy a new pair of docker images each time a release is made, and each time the workflow is manually run. It will create the following tags (where `<version>` is replaced by the most recent 'release' version.):

## At this moment (17-01-2023) version 3.8 images are not provided yet and will come very soon

* `<version>-h2`
* `<version>-maria`
* `<version>-postgres`
* `latest-h2`
* `latest-maria`
* `latest-postgres` 
* `<version>-h2-testnet`
* `<version>-maria-testnet`
* `<version>-postgres-testnet`
* `latest-h2-testnet`
* `latest-maria-testnet`
* `latest-postgres-testnet`

To manually deploy images, click on Github Actions, choose the 'Publish Docker Image' workflow, the click the 'Run Workflow' button, and choose a branch, then click 'Run'.


[docker-signup]: https://hub.docker.com/signup "Docker Signup"
