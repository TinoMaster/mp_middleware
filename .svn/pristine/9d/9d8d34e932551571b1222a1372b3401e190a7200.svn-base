#!/bin/sh

# Recupera il file JAR piu' recente nella directory corrente
EXEC_FILE=$(ls -t mypay.mypaycore-springboot* | head -n 1)

# Avvia l'applicazione in background con il profilo dev attivo
nohup java -jar "$EXEC_FILE" --spring.profiles.active=dev &
