import { render, screen } from '@testing-library/react-native';
import React from 'react';

import Home from '../app/index';

describe('app/index (Home)', () => {
  it('renderiza a tela do operador', () => {
    render(<Home />);
    expect(screen.getByText('MONITORIZAÇÃO DA LINHA 01')).toBeTruthy();
  });
});
