package org.ggp.base.apps.utilities;

import org.ggp.base.util.gdl.factory.exceptions.GdlFormatException;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.symbol.factory.exceptions.SymbolFormatException;

import java.io.IOException;

public class GameServerRunnerMany {

    public static void main(String[] args) throws IOException, SymbolFormatException, GdlFormatException, InterruptedException, GoalDefinitionException
    {
        // Чтение параметров из переменных окружения
        String tournamentName = System.getenv("TOURNAMENT_NAME") != null ? 
                System.getenv("TOURNAMENT_NAME") : "MyTur";
                
        String gameName = System.getenv("GAME_NAME") != null ? 
                System.getenv("GAME_NAME") : "ticTacToe";
                
        String startClock = System.getenv("START_CLOCK") != null ? 
                System.getenv("START_CLOCK") : "5";
                
        String playClock = System.getenv("PLAY_CLOCK") != null ? 
                System.getenv("PLAY_CLOCK") : "5";
                
        // Фиксированные порты и игроки согласно XML-конфигурациям
        String mctsPort = "9147";
        String randomPort = "9148";
        String mctsName = "MCTSGamer";
        String randomName = "RandomGamer";
        
        // Локальный хост для запуска внутри одного контейнера
        String hostName = "127.0.0.1";
                
        int numberOfGames = System.getenv("NUMBER_OF_GAMES") != null ? 
                Integer.parseInt(System.getenv("NUMBER_OF_GAMES")) : 1;
                
        System.out.println("Starting " + numberOfGames + " games with parameters:");
        System.out.println("Tournament: " + tournamentName);
        System.out.println("Game: " + gameName);
        System.out.println("Start clock: " + startClock + ", Play clock: " + playClock);
        System.out.println("MCTS Player: " + mctsName + " at " + hostName + ":" + mctsPort);
        System.out.println("Random Player: " + randomName + " at " + hostName + ":" + randomPort);
                
        // Запуск указанного количества игр
        for (int i = 0; i < numberOfGames; i++) {
            System.out.println("\n========= Starting game " + (i+1) + " of " + numberOfGames + " =========\n");
            
            GameServerRunner.main(new String[] {
                tournamentName,
                gameName,
                startClock,
                playClock,
                hostName,
                mctsPort,
                mctsName,
                hostName,
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
    }
}