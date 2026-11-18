package com.pi.demo.validacao;

import com.fazecast.jSerialComm.SerialPort;

public class comunicacaoSerial {
    public static void main(String[] args) {
        // ATENÇÃO: Altere para a sua porta real (Ex: "COM3" no Windows ou "/dev/ttyUSB0" no Linux)
        String nomePorta = "COM7"; 
        
        SerialPort porta = SerialPort.getCommPort(nomePorta);
        porta.setBaudRate(115200);
        porta.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 2000, 0);

        if (porta.openPort()) {
            System.out.println("Sucesso: Porta " + nomePorta + " aberta.");
        } else {
            System.out.println("Erro: Não foi possível abrir a porta. Verifique se ela está ocupada.");
            return;
        }

        try {
            // Pequena pausa obrigatória para estabilizar o reset automático do ESP8266 via USB
            Thread.sleep(2500);

            // 1. ESCRITA NA SERIAL (Envia o comando '1' para o ESP)
            byte[] comandoEnviar = {'1'};
            int bytesEscritos = porta.writeBytes(comandoEnviar, comandoEnviar.length);
            System.out.println("Java -> Enviado " + bytesEscritos + " byte(s): '1'");

            // Aguarda o ESP processar e responder
            Thread.sleep(500);

            // 2. LEITURA DA SERIAL (Lê o retorno enviado pelo ESP)
            byte[] bufferLeitura = new byte[1024];
            int bytesLidos = porta.readBytes(bufferLeitura, bufferLeitura.length);

            if (bytesLidos > 0) {
                String respostaEsp = new String(bufferLeitura, 0, bytesLidos);
                System.out.println("Java <- Resposta recebida do ESP:\n" + respostaEsp.trim());
            } else {
                System.out.println("Aviso: Nenhum dado retornado pela porta a tempo.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Fecha a porta de forma segura
            porta.closePort();
            System.out.println("Porta serial fechada com sucesso.");
        }
    }
}
