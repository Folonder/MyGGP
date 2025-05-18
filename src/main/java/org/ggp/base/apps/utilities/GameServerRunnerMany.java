package org.ggp.base.apps.utilities;

import org.ggp.base.util.gdl.factory.exceptions.GdlFormatException;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.symbol.factory.exceptions.SymbolFormatException;

import java.io.IOException;

public class GameServerRunnerMany {

    public static void main(String[] args) throws IOException, SymbolFormatException, GdlFormatException, InterruptedException, GoalDefinitionException
    {
        // Получение параметров из аргументов командной строки
        String tournamentName = args.length > 0 ? args[0] : "MyTur";
        String gameName = args.length > 1 ? args[1] : "ticTacToe";
        String startClock = args.length > 2 ? args[2] : "5";
        String playClock = args.length > 3 ? args[3] : "5";

        // Получение информации об игроках из аргументов
        String mctsHost = args.length > 4 ? args[4] : "mcts-player";
        String mctsPort = args.length > 5 ? args[5] : "9147";
        String mctsName = args.length > 6 ? args[6] : "MCTSGamer";
        String randomHost = args.length > 7 ? args[7] : "random-player";
        String randomPort = args.length > 8 ? args[8] : "9148";
        String randomName = args.length > 9 ? args[9] : "RandomGamer";

        // Число игр по-прежнему можно брать из переменной окружения
        int numberOfGames = System.getenv("NUMBER_OF_GAMES") != null ?
                Integer.parseInt(System.getenv("NUMBER_OF_GAMES")) : 1;

        System.out.println("Starting " + numberOfGames + " games with parameters:");
        System.out.println("Tournament: " + tournamentName);
        System.out.println("Game: " + gameName);
        System.out.println("Start clock: " + startClock + ", Play clock: " + playClock);
        System.out.println("MCTS Player: " + mctsName + " at " + mctsHost + ":" + mctsPort);
        System.out.println("Random Player: " + randomName + " at " + randomHost + ":" + randomPort);

        // Запуск указанного количества игр
        for (int i = 0; i < numberOfGames; i++) {
            System.out.println("\n========= Starting game " + (i+1) + " of " + numberOfGames + " =========\n");

            GameServerRunner.main(new String[] {
                    tournamentName,
                    gameName,
                    startClock,
                    playClock,
                    mctsHost,
                    mctsPort,
                    mctsName,
                    randomHost,
                    randomPort,
                    randomName
            });

            // Небольшая пауза между играми
            if (i < numberOfGames - 1) {
                System.out.println("Waiting 5 seconds before starting next game...");
                Thread.sleep(5000);
            }
        }

        System.out.println("\n========= All " + numberOfGames + " games completed =========\n");
        System.exit(0);
    }
}