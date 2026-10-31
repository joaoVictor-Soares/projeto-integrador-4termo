import { render, screen } from '@testing-library/react-native';
import React from 'react';
import { StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Colors } from '@/constants/theme';

jest.mock('@/hooks/use-color-scheme', () => ({ useColorScheme: jest.fn() }));
import { useColorScheme } from '@/hooks/use-color-scheme';
const mockScheme = useColorScheme;

describe('ThemedText', () => {
  it('aplica a cor do tema claro por padrão', () => {
    mockScheme.mockReturnValue('light');
    render(<ThemedText>olá</ThemedText>);
    expect(StyleSheet.flatten(screen.getByText('olá').props.style).color).toBe(Colors.light.text);
  });

  it('aplica a cor do tema escuro', () => {
    mockScheme.mockReturnValue('dark');
    render(<ThemedText>olá</ThemedText>);
    expect(StyleSheet.flatten(screen.getByText('olá').props.style).color).toBe(Colors.dark.text);
  });

  it('trata scheme "unspecified" como claro', () => {
    mockScheme.mockReturnValue('unspecified');
    render(<ThemedText themeColor="textSecondary">x</ThemedText>);
    expect(StyleSheet.flatten(screen.getByText('x').props.style).color).toBe(Colors.light.textSecondary);
  });

  it.each(['title', 'small', 'smallBold', 'subtitle', 'link', 'linkPrimary', 'code'])(
    'renderiza o tipo %s',
    (type) => {
      mockScheme.mockReturnValue('light');
      render(<ThemedText type={type}>t</ThemedText>);
      expect(screen.getByText('t')).toBeTruthy();
    },
  );
});

describe('ThemedView', () => {
  it('usa o background do tema e permite escolher outro token', () => {
    mockScheme.mockReturnValue('dark');
    render(
      <>
        <ThemedView testID="a" />
        <ThemedView testID="b" type="backgroundElement" />
      </>,
    );
    expect(StyleSheet.flatten(screen.getByTestId('a').props.style).backgroundColor).toBe(Colors.dark.background);
    expect(StyleSheet.flatten(screen.getByTestId('b').props.style).backgroundColor).toBe(
      Colors.dark.backgroundElement,
    );
  });
});
