#!/usr/bin/env bash
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo "Options:"
    echo "-h, --help                    Display this help message"
    echo "-b, --build                   Compile and Build the current gradle project"
    echo "-c, --continuous              Compile, Build and Serve using local configs in a continuous way"
    echo "-d, --stop                    Stop the continuous built process"
    echo "-t, --test                    Compile, Build and Test the current gradle project"
    echo "-l, --local                   Compile, Build and Serve using local configs"
    echo "-s, --stag,  --staging        Compile, Build and Serve using stating configs"
    echo "-p, --prod,  --production     Compile, Build and Serve using production configs"
}

handle_options() {
    while [ $# -gt 0 ]; do
        case $1 in
            -h | --help)
                usage
                exit 0
            ;;

            -b | --build)
                gradle clean
                gradle build -x test
            ;;

            -c | --continuous)
                gradle clean
                gradle build -x test --continuous --quiet &
                gradle bootRun --args='--spring.profiles.active=local'
            ;;

            -d | --stop)
                gradle --stop
            ;;

            -t | --test)
                gradle clean
                gradle test --continuous
            ;;

            -l | --local)
                gradle clean
                gradle build -x test
                java -Dspring.profiles.active=local -jar /src/build/libs/quant-ai-persistence-middleware.jar
            ;;

            -s | --stag | --staging)
                gradle clean
                gradle build -x test
                java -Dspring.profiles.active=stag -jar /src/build/libs/quant-ai-persistence-middleware.jar
            ;;

            -p | --prod | --production)
                gradle clean
                gradle build -x test
                java -Dspring.profiles.active=prod -jar /src/build/libs/quant-ai-persistence-middleware.jar
            ;;

            *)
                echo "Invalid option: $1" >&2
                usage
                exit 1
            ;;
        esac
        shift
    done
}

handle_options "$@"