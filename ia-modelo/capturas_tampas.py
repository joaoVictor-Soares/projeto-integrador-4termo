import cv2
import os
from datetime import datetime

# Cria a pasta de destino se não existir
PASTA_DESTINO = "aprovada"
os.makedirs(PASTA_DESTINO, exist_ok=True)

def melhorar_qualidade_imagem(imagem):
    """
    Função para melhorar a qualidade da imagem recortada para o PyTorch:
    - Aplica CLAHE para normalizar a iluminação e reflexos.
    - Aplica nitidez para realçar os detalhes da tampinha.
    """
    if imagem.size == 0:
        return imagem
    
    lab = cv2.cvtColor(imagem, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=2.5, tileGridSize=(8, 8))
    l_cl = clahe.apply(l)
    lab_merged = cv2.merge((l_cl, a, b))
    imagem_corrigida = cv2.cvtColor(lab_merged, cv2.COLOR_LAB2BGR)
    
    suave = cv2.GaussianBlur(imagem_corrigida, (0, 0), 1.0)
    nitida = cv2.addWeighted(imagem_corrigida, 1.5, suave, -0.5, 0)
    return nitida

def main():
    cap = cv2.VideoCapture(0)
    
    # Configuração da resolução da câmera
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)

    if not cap.isOpened():
        print("Erro: Não foi possível acessar a câmera.")
        return

    print("=== Sistema de Captura Direcional Iniciado ===")
    print("Pressione 'q' para sair do programa.")

    cooldown = 0
    tampa_ja_processada = False

    while True:
        ret, frame = cap.read()
        if not ret:
            print("Aviso: Falha ao capturar frame da câmera. Tentando novamente...")
            continue

        altura, largura, _ = frame.shape

        # 1. Linha limite vertical à esquerda (posição de disparo)
        pos_linha_x = int(largura * 0.40)
        cv2.line(frame, (pos_linha_x, 0), (pos_linha_x, altura), (0, 0, 255), 3)

        # 2. Restringe a busca verticalmente para ignorar o topo/fundo da esteira
        y_min = int(altura * 0.30)
        y_max = int(altura * 0.70)
        faixa_esteira = frame[y_min:y_max, :]

        # 3. Tratamento de cor HSV para encontrar a tampinha verde
        hsv = cv2.cvtColor(faixa_esteira, cv2.COLOR_BGR2HSV)
        inferior_verde = (35, 50, 50)
        superior_verde = (85, 255, 255)
        mascara = cv2.inRange(hsv, inferior_verde, superior_verde)

        # Limpeza de ruído
        kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
        mascara = cv2.morphologyEx(mascara, cv2.MORPH_OPEN, kernel)
        mascara = cv2.morphologyEx(mascara, cv2.MORPH_CLOSE, kernel)

        contornos, _ = cv2.findContours(mascara, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        
        tampa_no_gatilho = False
        centro_tampa_x = 0
        centro_tampa_y = 0

        for c in contornos:
            area = cv2.contourArea(c)
            
            # Filtro de tamanho para validar a tampinha
            if 1000 < area < 80000:
                (bx, by, bw, bh) = cv2.boundingRect(c)
                by_global = by + y_min
                
                aspect_ratio = float(bw) / float(bh) if bh > 0 else 0
                if 0.4 <= aspect_ratio <= 2.5:
                    centro_x = bx + (bw // 2)
                    centro_y = by_global + (bh // 2)

                    # Desenha o retângulo verde ao redor da peça ao vivo
                    cv2.rectangle(frame, (bx, by_global), (bx + bw, by_global + bh), (0, 255, 0), 2)

                    # DISPARO CORRIGIDO: A tampinha move-se da direita para a esquerda.
                    # Dispara quando o centro da peça cruza ou atinge a linha vermelha (<= pos_linha_x)
                    if centro_x <= pos_linha_x:
                        tampa_no_gatilho = True
                        centro_tampa_x = centro_x
                        centro_tampa_y = centro_y
                        break

        if tampa_no_gatilho and not tampa_ja_processada and cooldown == 0:
            print(f"[SUCESSO] Tampinha cruzou a linha limite! A salvar imagens...")
            
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
            
            # PASSO A: Salva a foto inteira original da câmera
            caminho_full = os.path.join(PASTA_DESTINO, f"tampa_full_{timestamp}.jpg")
            cv2.imwrite(caminho_full, frame)
            print(f"-> Foto inteira salva em: {caminho_full}")

            # PASSO B: Faz o recorte centralizado e melhorado a partir da foto inteira
            raio_recorte = 160 # Raio em pixels ao redor do centro da tampinha
            rx1 = max(0, centro_tampa_x - raio_recorte)
            ry1 = max(0, centro_tampa_y - raio_recorte)
            rx2 = min(largura, centro_tampa_x + raio_recorte)
            ry2 = min(altura, centro_tampa_y + raio_recorte)
            
            tampa_recortada = frame[ry1:ry2, rx1:rx2]

            if tampa_recortada.size > 0:
                imagem_melhorada = melhorar_qualidade_imagem(tampa_recortada)
                caminho_crop = os.path.join(PASTA_DESTINO, f"tampa_aprovada_{timestamp}.jpg")
                cv2.imwrite(caminho_crop, imagem_melhorada)
                print(f"-> Foto recortada, centralizada e tratada salva em: {caminho_crop}")

            cooldown = 100 # Pausa para evitar múltiplas capturas da mesma peça na linha
            tampa_ja_processada = True
        elif not tampa_no_gatilho:
            # Reseta o estado quando a peça passa da linha para permitir a próxima tampinha
            tampa_ja_processada = False

        if cooldown > 0:
            cooldown -= 1

        # Exibe as janelas de monitoramento
        cv2.imshow("Monitoramento - Câmera ao Vivo", frame)
        cv2.imshow("Mascara HSV (Filtro)", mascara)

        if cv2.waitKey(1) & 0xFF == ord('q'):
            break

    cap.release()
    cv2.destroyAllWindows()

if __name__ == "__main__":
    main()