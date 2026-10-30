package com.pi.demo.poc;
/*
 * POC: conexão OPC-UA com CLP e acionamento de atuadores (Java puro + Eclipse Milo).
 *
 * Dependência Maven:
 *   <dependency>
 *     <groupId>org.eclipse.milo</groupId>
 *     <artifactId>sdk-client</artifactId>
 *     <version>0.6.15</version>
 *   </dependency>
 *
 * Execução (exemplo):
 *   java ClpOpcUaPoc opc.tcp://192.168.0.10:4840
 *
 * Ajuste os NodeIds abaixo conforme o mapeamento de variáveis do seu CLP
 * (dica: use o UaExpert para navegar no servidor e copiar os NodeIds).
 */

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;

public class ClpOpcUaPoc {

    // ---- CONFIGURAÇÃO (ajuste para o seu CLP) ----
    private static final String DEFAULT_ENDPOINT = "opc.tcp://192.168.0.10:4840";

    // Cilindro de SIMPLES ação: desvia peça reprovada
    private static final String NODE_CILINDRO_SIMPLES = "ns=2;s=GVL.cilindro_simples";
    // Cilindro de DUPLA ação: movimenta peça aprovada
    private static final String NODE_CILINDRO_DUPLA_AVANCA = "ns=2;s=GVL.cilindro_dupla_avanca";
    private static final String NODE_CILINDRO_DUPLA_RECUA = "ns=2;s=GVL.cilindro_dupla_recua";
    // Sensor (leitura) - opcional
    private static final String NODE_SENSOR_PECA = "ns=2;s=GVL.sensor_peca";

    private static final long PULSO_MS = 1000;

    public static void main(String[] args) throws Exception {
        String endpoint = args.length > 0 ? args[0] : DEFAULT_ENDPOINT;

        System.out.println("Conectando em " + endpoint + " ...");
        OpcUaClient client = OpcUaClient.create(endpoint); // acesso anônimo, sem segurança
        try {
            client.connect().get(10, TimeUnit.SECONDS);
            System.out.println("Conectado ao CLP com sucesso!\n");
            menu(client);
        } catch (Exception e) {
            System.err.println("Falha na conexão/operação: " + e.getMessage());
        } finally {
            client.disconnect().get(5, TimeUnit.SECONDS);
            System.out.println("Desconectado.");
        }
    }

    private static void menu(OpcUaClient client) throws Exception {
        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.println("""
                    ===== POC CLP (OPC-UA) =====
                    1 - Ler sensor de peça
                    2 - Acionar cilindro SIMPLES (pulso, desvia peça)
                    3 - Cilindro DUPLA: avançar
                    4 - Cilindro DUPLA: recuar
                    5 - Ler variável por NodeId (qualquer)
                    0 - Sair
                    """);
            System.out.print("Opção: ");
            String op = in.nextLine().trim();
            try {
                switch (op) {
                    case "1" -> System.out.println("Sensor = " + read(client, NODE_SENSOR_PECA));
                    case "2" -> pulso(client, NODE_CILINDRO_SIMPLES);
                    case "3" -> {
                        write(client, NODE_CILINDRO_DUPLA_RECUA, false);
                        write(client, NODE_CILINDRO_DUPLA_AVANCA, true);
                    }
                    case "4" -> {
                        write(client, NODE_CILINDRO_DUPLA_AVANCA, false);
                        write(client, NODE_CILINDRO_DUPLA_RECUA, true);
                    }
                    case "5" -> {
                        System.out.print("NodeId (ex: ns=2;s=GVL.x): ");
                        System.out.println("Valor = " + read(client, in.nextLine().trim()));
                    }
                    case "0" -> {
                        return;
                    }
                    default -> System.out.println("Opção inválida.");
                }
            } catch (Exception e) {
                System.err.println("Erro: " + e.getMessage());
            }
            System.out.println();
        }
    }

    private static Object read(OpcUaClient client, String nodeIdStr) throws Exception {
        NodeId nodeId = NodeId.parse(nodeIdStr);
        DataValue dv = client.readValue(0.0, TimestampsToReturn.Both, nodeId).get(5, TimeUnit.SECONDS);
        if (dv.getStatusCode() != null && dv.getStatusCode().isBad()) {
            throw new IllegalStateException("Status ruim ao ler " + nodeIdStr + ": " + dv.getStatusCode());
        }
        return dv.getValue().getValue();
    }

    private static void write(OpcUaClient client, String nodeIdStr, boolean value) throws Exception {
        NodeId nodeId = NodeId.parse(nodeIdStr);
        var status = client.writeValue(nodeId, new DataValue(new Variant(value))).get(5, TimeUnit.SECONDS);
        if (status.isBad()) {
            throw new IllegalStateException("Falha ao escrever " + nodeIdStr + ": " + status);
        }
        System.out.println("Escrito " + nodeIdStr + " = " + value);
    }

    /** Liga a saída, aguarda PULSO_MS e desliga (garante desligar mesmo com erro). */
    private static void pulso(OpcUaClient client, String nodeIdStr) throws Exception {
        write(client, nodeIdStr, true);
        try {
            Thread.sleep(PULSO_MS);
        } finally {
            write(client, nodeIdStr, false);
        }
    }
}