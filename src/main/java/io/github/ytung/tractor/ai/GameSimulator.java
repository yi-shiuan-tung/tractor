
package io.github.ytung.tractor.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import io.github.ytung.tractor.Game;
import io.github.ytung.tractor.PlayResult;
import io.github.ytung.tractor.api.FindAFriendDeclaration;
import io.github.ytung.tractor.api.GameStatus;
import io.github.ytung.tractor.api.Play;

public class GameSimulator {

    /**
     * Simulate a Tractor game. Returns the number of points that the opponents won.
     */
    public int runGame(
            List<AiClient> aiClients,
            int numDecks,
            boolean findAFriend) throws Exception {
        Game game = new Game();
        game.setRoundNumber(1);

        List<String> playerIds = new ArrayList<>();
        for (int i = 0; i < aiClients.size(); i++) {
            String playerId = UUID.randomUUID().toString();
            game.addPlayer(playerId);
            playerIds.add(playerId);
        }

        game.setNumDecks(numDecks);
        game.setFindAFriend(findAFriend);

        game.startRound();

        while (true) {
            int currentPlayerIndex = game.getCurrentPlayerIndex();
            String playerId = playerIds.get(currentPlayerIndex);

            Play draw = game.draw();
            Collection<Integer> declare = aiClients.get(currentPlayerIndex).declare(playerId, game);
            if (declare != null)
                game.declare(playerId, new ArrayList<>(declare));
            if (draw == null)
                break;
        }

        {
            int starterPlayerIndex = game.getStarterPlayerIndex();
            String playerId = playerIds.get(starterPlayerIndex);
            game.takeKitty();
            Collection<Integer> kitty = aiClients.get(starterPlayerIndex).makeKitty(playerId, game);
            game.makeKitty(playerId, new ArrayList<>(kitty));

            if (findAFriend) {
                FindAFriendDeclaration declaration = aiClients.get(starterPlayerIndex).setFindAFriendDeclaration(playerId, game);
                game.makeFindAFriendDeclaration(playerId, declaration);
            }
        }

        while (true) {
            int currentPlayerIndex = game.getCurrentPlayerIndex();
            String playerId = playerIds.get(currentPlayerIndex);

            Collection<Integer> play = aiClients.get(currentPlayerIndex).play(playerId, game);
            PlayResult result = game.play(playerId, new ArrayList<>(play), true);
            if (result.isTrickComplete()) {
                game.finishTrick();
                if (game.getStatus() != GameStatus.PLAY)
                    break;
            }
        }

        int numOpponentsPoints = 0;
        for (String playerId : playerIds)
            if (!game.getIsDeclaringTeam().get(playerId))
                numOpponentsPoints += game.getCurrentRoundScores().get(playerId);
        return numOpponentsPoints;
    }

    public static void main(String[] args) throws Exception {
        List<AiClient> aiClients = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            aiClients.add(i % 2 == 0 ? new BayesianAiClientV2() : new BayesianAiClient());
        int numDecks = 2;
        boolean findAFriend = false;

        GameSimulator gameSimulator = new GameSimulator();

        long startTime = System.currentTimeMillis();
        List<Integer> numOpponentsPoints = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            System.out.printf("Running game %d...\n", i + 1);
            numOpponentsPoints.add(gameSimulator.runGame(aiClients, numDecks, findAFriend));
        }

        double averageOpponentsPoints = numOpponentsPoints.stream().mapToDouble(points -> points).sum()
                / numOpponentsPoints.size();
        // 90% confidence interval
        double confidenceInterval = 1.645 * Math.sqrt(numOpponentsPoints.stream()
            .mapToDouble(points -> Math.pow(points - averageOpponentsPoints, 2))
            .sum() / (numOpponentsPoints.size() - 1) / numOpponentsPoints.size());
        System.out.println("Number of games: " + numOpponentsPoints.size());
        System.out.println("Total time: " + (System.currentTimeMillis() - startTime));
        System.out.printf("Average score: %.3f ± %.3f\n", averageOpponentsPoints, confidenceInterval);
    }
}
