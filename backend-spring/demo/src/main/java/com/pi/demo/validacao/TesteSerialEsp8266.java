package com.pi.demo.validacao;

import java.util.Scanner;

import com.fazecast.jSerialComm.SerialPort;

/**
 * Ferramenta ISOLADA (sem Spring) para validar a comunicacao serial Java &lt;-&gt; ESP8266
 * NodeMCU (ESP-12E) antes de ligar o backend inteiro. Envia comandos pela serial E le a
 * serial do ESP ao mesmo tempo, usando a MESMA framing do protocolo definitivo do
 * projeto (EspGateway/EspSerialGateway): enderecamento de bits, valores sempre LOGICOS.
 *
 *   Pi -> ESP (comando, exatamente como o AtuadorServiceImpl manda em producao):
 *       "S<endereco>:<0|1>\n"
 *   ESP -> Pi (leitura, exatamente como o SensorPollingService le em producao):
 *       "E<endereco>:<0|1>\n"
 *
 * Use junto com teste_serial_esp8266.ino (mesma Entrega): aquele firmware le de verdade
 * um pino de entrada (D2) e escreve de verdade um pino de saida (D1, baixo-ativo) - isso
 * aqui nao e so um eco, e o comportamento real que o ESP vai ter quando integrado.
 *
 * Uso:
 *   - Esta ferramenta ESCREVE: digite 0 ou 1 e ENTER para mandar "S0:<numero>" ao ESP
 *     (liga/desliga a saida D1 de teste).
 *   - Esta ferramenta LE: uma thread de fundo fica recebendo "E0:<valor>" o tempo todo
 *     (o ESP reporta a cada ~50ms) e so IMPRIME quando o valor muda - assim da pra ver,
 *     em tempo real, o botao/jumper ligado no D2 do ESP sendo pressionado e solto.
 *
 * Compilar/rodar (jSerialComm precisa estar no classpath - o Maven ja baixou um jar em
 * ~/.m2/repository/com/fazecast/jSerialComm/2.11.0/, ou baixe avulso em
 * https://repo1.maven.org/maven2/com/fazecast/jSerialComm/2.11.0/jSerialComm-2.11.0.jar):
 *
 *   javac -cp jSerialComm-2.11.0.jar TesteSerialEsp8266.java
 *   java  -cp .:jSerialComm-2.11.0.jar TesteSerialEsp8266 /dev/ttyUSB0
 */
public class TesteSerialEsp8266 {

    private static final int ENDERECO_TESTE = 0; // mesmo endereco usado em producao (sensor/cilindro)
    private static final int BAUD_RATE = 9600;

    public static void main(String[] args) throws Exception {
        // String nomePorta = args.length > 0 ? args[0] : "/dev/ttyUSB0";
        String nomePorta = "COM7";
        SerialPort porta = SerialPort.getCommPort(nomePorta);
        porta.setComPortParameters(BAUD_RATE, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        porta.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0);

        if (!porta.openPort()) {
            System.err.println("Nao foi possivel abrir a porta " + nomePorta + ". Confira o cabo/porta e tente de novo.");
            System.exit(1);
        }
        System.out.println("Porta " + nomePorta + " aberta a " + BAUD_RATE + " bps (ESP8266 NodeMCU ESP-12E).");
        System.out.println("Endereco de teste: " + ENDERECO_TESTE + " (mesmo do sensor/cilindro em producao)");
        System.out.println("Entrada real do ESP: pino D2 (GPIO4, INPUT_PULLUP)");
        System.out.println("Saida real do ESP:   pino D1 (GPIO5, baixo-ativo)");

        // LEITURA continua, em paralelo com a ESCRITA feita abaixo pelo terminal - exatamente
        // como o backend faz (SensorPollingService le o tempo todo; AtuadorServiceImpl escreve
        // quando precisa), so que aqui tudo roda num unico programa para facilitar o teste.
        Thread leitor = new Thread(() -> lerContinuamente(porta), "leitor-serial");
        leitor.setDaemon(true);
        leitor.start();

        System.out.println();
        System.out.println("Digite 0 ou 1 e ENTER para mandar \"S" + ENDERECO_TESTE
                + ":<numero>\" ao ESP (liga/desliga a saida D1 de teste).");
        System.out.println("Digite \"sair\" para encerrar.");

        try (Scanner teclado = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                String linha = teclado.nextLine().trim();
                if (linha.equalsIgnoreCase("sair")) {
                    break;
                }
                if (!linha.equals("0") && !linha.equals("1")) {
                    System.out.println("Digite apenas 0, 1 ou \"sair\".");
                    continue;
                }
                String frame = "S" + ENDERECO_TESTE + ":" + linha + "\n";
                porta.getOutputStream().write(frame.getBytes());
                porta.getOutputStream().flush();
                System.out.println("Enviado: " + frame.trim());
            }
        } finally {
            porta.closePort();
            System.out.println("Porta fechada. Ate mais.");
        }
    }

    /**
     * Le continuamente os frames "E<endereco>:<valor>" que o ESP manda (a cada ~50ms, como em
     * producao) e so imprime quando o valor muda - senao o terminal fica poluido, ja que o
     * ESP reporta o tempo todo mesmo sem nada ter mudado (igual ao SensorPollingService faz
     * com a borda de subida, so que aqui mostramos qualquer mudanca, subida ou descida).
     */
    private static void lerContinuamente(SerialPort porta) {
        String ultimoValorImpresso = null;
        try {
            java.io.BufferedReader leitor = new java.io.BufferedReader(
                    new java.io.InputStreamReader(porta.getInputStream()));
            while (porta.isOpen()) {
                String linha = leitor.readLine();
                if (linha == null || linha.isBlank()) {
                    continue; // timeout de leitura (semi-blocking): normal, so tenta de novo
                }
                linha = linha.trim();
                if (!linha.equals(ultimoValorImpresso)) {
                    ultimoValorImpresso = linha;
                    System.out.println();
                    System.out.println("Recebido do ESP (mudou): " + linha);
                    System.out.print("> ");
                }
            }
        } catch (Exception e) {
            if (porta.isOpen()) {
                System.err.println("Erro na leitura serial: " + e.getMessage());
            }
        }
    }
}
