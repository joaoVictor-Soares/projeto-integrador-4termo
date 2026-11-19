package com.pi.demo.poc;

import org.apache.plc4x.java.DefaultPlcDriverManager;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;

import java.util.concurrent.TimeUnit;

public class ClpSequenciaPoc {

    private static final String CONNECTION_STRING = "s7:tcp://192.168.0.1?rack=0&slot=1";

    public static void main(String[] args) {
        System.out.println("Conectando ao CLP Siemens S7 na porta 102...");

        try (PlcConnection plcConnection = new DefaultPlcDriverManager().getConnection(CONNECTION_STRING)) {

            if (!plcConnection.getMetadata().isWriteSupported()) {
                System.err.println("O CLP conectado não suporta operações de escrita!");
                return;
            }

            System.out.println("Conectado com sucesso! Iniciando sequência da mesa...\n");

            // 1. LIGAR A ESTEIRA E OS 3 ATUADORES
            System.out.println("Ligando esteira e atuadores...");
            escreverMultiplos(plcConnection, true);

            // 2. ESPERAR 3 SEGUNDOS
            System.out.println("Aguardando 3 segundos...");
            Thread.sleep(3000);

            // 3. DESLIGAR TUDO
            System.out.println("Desligando tudo...");
            escreverMultiplos(plcConnection, false);

            System.out.println("\nSequência finalizada com sucesso!");

        } catch (Exception e) {
            System.err.println("Erro durante a execução da sequência: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void escreverMultiplos(PlcConnection connection, boolean estado) throws Exception {
        PlcWriteRequest.Builder builder = connection.writeRequestBuilder();

        // Mapeamento direto pelas especificações do projeto FESTO
        builder.addTagAddress("esteira", "%Q0.4:BOOL", estado);
        builder.addTagAddress("atuador1", "%Q0.0:BOOL", estado);
        builder.addTagAddress("atuador2", "%Q0.1:BOOL", estado);
        builder.addTagAddress("atuador3", "%Q0.2:BOOL", estado);

        PlcWriteRequest writeRequest = builder.build();
        PlcWriteResponse response = writeRequest.execute().get(5, TimeUnit.SECONDS);

        // Validação rápida de resposta para cada tag
        for (String tagName : response.getTagNames()) {
            if (!response.getResponseCode(tagName).name().equals("OK")) {
                System.err.println("Aviso: Falha ao atualizar a tag " + tagName);
            }
        }
    }
}