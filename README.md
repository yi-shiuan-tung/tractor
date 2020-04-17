# tractor

Multiplayer online tractor game

![Screenshot](screenshot.png)

Features:

- Full ruleset; only valid plays are allowed
- Supports variable number of players and variable number of decks
- Supports find-a-friend version
- Supports special "does-it-fly" plays
- Sound notifications on your turn
- Shows the currently winning player in each trick
- View the most recent trick
- Automatic dealing
- AI players

## Quickstart

    npm install
    npm run build
    ./gradlew run

Go to http://localhost:8080.

## Development

### Eclipse

Run the following and then import the project in Eclipse:

    ./gradlew eclipse

Then start `TractorServer.java` in Eclipse.

### IntelliJ

Run:

    ./gradlew idea

And then open the generated `.ipr` file.

### Frontend

First start the dev server:

    npm install
    npm run start

Then go to http://localhost:3000. The site will auto-refresh after making any frontend changes.

