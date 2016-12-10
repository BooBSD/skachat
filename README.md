# Skachat



## Prerequisites

You will need [Leiningen][1] 2.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein run

## Deploy

First you need ansible:

    sudo pip install ansible

To deploy, run:

    ansible-playbook -i ansible/production.ini ansible/deploy.yml

## Backup

To backup, run:

    ansible-playbook -i ansible/production.ini ansible/backup.yml

You can find backup archive in ./backup/ ditectory.

## License

Copyright © 2016
