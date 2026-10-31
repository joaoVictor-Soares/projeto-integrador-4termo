import { fireEvent, render, screen } from '@testing-library/react-native';
import React from 'react';

import OperadorScreen from '../OperadorScreen';

describe('OperadorScreen', () => {
  it('renderiza título, status ATIVA e velocidade inicial de 80%', () => {
    render(<OperadorScreen />);
    expect(screen.getByText('MONITORIZAÇÃO DA LINHA 01')).toBeTruthy();
    expect(screen.getByText('ATIVA')).toBeTruthy();
    expect(screen.getByText('80%')).toBeTruthy();
  });

  it('mostra o placeholder do feed da câmera', () => {
    render(<OperadorScreen />);
    expect(screen.getByText(/AGUARDANDO SINAL DO SENSOR CAPACITIVO/)).toBeTruthy();
  });

  it('aumenta a velocidade em 10% ao tocar em +10%', () => {
    render(<OperadorScreen />);
    fireEvent.press(screen.getByText('+10%'));
    expect(screen.getByText('90%')).toBeTruthy();
  });

  it('diminui a velocidade em 10% ao tocar em -10%', () => {
    render(<OperadorScreen />);
    fireEvent.press(screen.getByText('-10%'));
    expect(screen.getByText('70%')).toBeTruthy();
  });

  it('limita a velocidade ao máximo de 100%', () => {
    render(<OperadorScreen />);
    for (let i = 0; i < 5; i++) fireEvent.press(screen.getByText('+10%'));
    expect(screen.getByText('100%')).toBeTruthy();
  });

  it('limita a velocidade ao mínimo de 0%', () => {
    render(<OperadorScreen />);
    for (let i = 0; i < 12; i++) fireEvent.press(screen.getByText('-10%'));
    expect(screen.getByText('0%')).toBeTruthy();
  });

  // Controles de segurança/negócio ainda NÃO implementados (OWASP Top 10:2025 - A01/A06).
  it.failing('A01 - só deve permitir alterar a velocidade para perfil autorizado', () => {
    render(<OperadorScreen />);
    // Hoje qualquer pessoa que abra o app altera a velocidade (sem login/perfil).
    expect(screen.queryByText('+10%')).toBeNull();
  });

  it.todo('A06 - alterar velocidade deve ser enviado ao backend e validado no servidor');
});
