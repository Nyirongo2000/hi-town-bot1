# Tag Me In Hi Town Bot

This bot, implemented in `Bot.kt`, is a bot that:

- Runs on https://hitown.chat Hi Town
- Connects to https://tagme.in as a data source

## Usage

Send messages on Hi Town in a group that has this bot installed, and working (server is running), send a message with the following format:

## Channel commands

### Show the top 10 channel messages
- `!tmi <channel name>`, where `<channel name>` would be replaced with `cars` or `ice cream`, etc. Channel names will be URL encoded before requested from Tag Me In.

# Ubuntu server setup guide

### 1. Install default set of dependencies

```bash
apt update
apt install default-jre nginx certbot python3-certbot-nginx
```

### 2. Configure Nginx

```bash
server {
    server_name <enter bot domain here>;

    location / {
        proxy_pass http://localhost:8080;
    }
}
```

### 3. Configure Certbot

```bash
certbot --nginx
service nginx restart
```

### 4. Run your bot

```bash
nohup java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -jar ./build/libs/tag-me-in-hi-town-bot-all.jar
 > log.txt 2> errors.txt < /dev/null &
```
### 5. Stop your bot

```bash
killall java
```