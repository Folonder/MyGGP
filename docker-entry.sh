#!/bin/bash

# Функция для запуска игрока
run_player() {
    PLAYER_PORT=$1
    PLAYER_CLASS=$2
    echo "Запуск игрока $PLAYER_CLASS на порту $PLAYER_PORT..."
    gradle playerRunner -Pport=$PLAYER_PORT -Pgamer=$PLAYER_CLASS
}

# Функция для запуска GameServer
run_game_server() {
    echo "Запуск GameServerRunnerMany с параметрами: $*"
    # Передаем аргументы в Java-приложение
    java -cp build/classes/java/main:build/resources/main:lib/Guava/guava-14.0.1.jar:lib/jackson-databind/jackson-databind-2.17.0.jar:lib/jackson-databind/jackson-core-2.17.0.jar:lib/jackson-databind/jackson-annotations-2.17.0.jar org.ggp.base.apps.utilities.GameServerRunnerMany $*
}

# Определяем, какой компонент запускать
COMPONENT=${1:-"all"}
shift

case "$COMPONENT" in
    "mcts")
        # Запуск MCTS игрока
        MCTS_PORT=${1:-"9147"}
        run_player $MCTS_PORT "MCTSGamer"
        ;;
    "random")
        # Запуск Random игрока
        RANDOM_PORT=${1:-"9148"}
        run_player $RANDOM_PORT "RandomGamer"
        ;;
    "server")
        # Запуск GameServer
        # Параметры: tournament_name game_key start_clock play_clock players...
        TOURNAMENT_NAME=${1:-"MyTournament"}
        GAME_KEY=${2:-"ticTacToe"}
        START_CLOCK=${3:-"5"}
        PLAY_CLOCK=${4:-"5"}
        shift 4
        
        # Формируем строку параметров
        ARGS="$TOURNAMENT_NAME $GAME_KEY $START_CLOCK $PLAY_CLOCK"
        
        # Добавляем информацию об игроках
        while [ $# -ge 3 ]; do
            ARGS="$ARGS $1 $2 $3"
            shift 3
        done
        
        run_game_server $ARGS
        ;;
    "all")
        # Запускаем все компоненты последовательно
        echo "Запуск всех компонентов..."
        
        # Параметры для GameServer
        TOURNAMENT_NAME=${1:-"MyTournament"}
        GAME_KEY=${2:-"ticTacToe"}
        START_CLOCK=${3:-"5"}
        PLAY_CLOCK=${4:-"5"}
        MCTS_HOST=${5:-"mcts-player"}
        MCTS_PORT=${6:-"9147"}
        MCTS_NAME=${7:-"MCTS_Player"}
        RANDOM_HOST=${8:-"random-player"}
        RANDOM_PORT=${9:-"9148"}
        RANDOM_NAME=${10:-"Random_Player"}
        
        # Запускаем сервер с игроками
        run_game_server "$TOURNAMENT_NAME" "$GAME_KEY" "$START_CLOCK" "$PLAY_CLOCK" \
                        "$MCTS_HOST" "$MCTS_PORT" "$MCTS_NAME" \
                        "$RANDOM_HOST" "$RANDOM_PORT" "$RANDOM_NAME"
        ;;
    *)
        echo "Неизвестный компонент: $COMPONENT"
        echo "Использование: $0 [mcts|random|server|all] [параметры]"
        exit 1
        ;;
esac