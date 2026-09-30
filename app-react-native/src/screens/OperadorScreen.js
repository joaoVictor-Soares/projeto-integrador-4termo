import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';

export default function OperadorScreen() {
  const [statusEsteira, setStatusEsteira] = useState('ATIVA');
  const [velocidade, setVelocidade] = useState(80);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>[ Sistema Automatizado de Inspeção de Peças com IA e IoT ]</Text>
        <Text style={styles.subtitle}>MONITORIZAÇÃO DA LINHA 01</Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.label}>{'>'} STATUS DA ESTEIRA:</Text>
        <Text style={[styles.value, statusEsteira === 'ATIVA' ? styles.colorGreen : styles.colorRed]}>
          {statusEsteira}
        </Text>

        <View style={styles.divider} />

        <Text style={styles.label}>{'>'} VELOCIDADE ATUAL:</Text>
        <Text style={styles.value}>{velocidade}%</Text>

        <View style={styles.buttonContainer}>
          <TouchableOpacity style={styles.button} onPress={() => setVelocidade(v => Math.max(0, v - 10))}>
            <Text style={styles.buttonText}>-10%</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.button} onPress={() => setVelocidade(v => Math.min(100, v + 10))}>
            <Text style={styles.buttonText}>+10%</Text>
          </TouchableOpacity>
        </View>
      </View>

      <View style={styles.cameraContainer}>
        <Text style={styles.label}>{'>'} FEED DA CÂMARA (EDGE IA):</Text>
        <View style={styles.cameraPlaceholder}>
          <Text style={styles.cameraText}>AGUARDANDO SINAL DO SENSOR CAPACITIVO...</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#050505',
    padding: 20,
  },
  header: {
    marginTop: 40,
    marginBottom: 30,
    borderBottomWidth: 1,
    borderBottomColor: '#00ff00',
    paddingBottom: 10,
  },
  title: {
    fontSize: 28,
    color: '#00ff00',
    fontFamily: 'monospace',
    fontWeight: 'bold',
    letterSpacing: 2,
  },
  subtitle: {
    fontSize: 14,
    color: '#00aa00',
    fontFamily: 'monospace',
    marginTop: 5,
    letterSpacing: 1,
  },
  card: {
    backgroundColor: '#0a0a0a',
    borderWidth: 1,
    borderColor: '#00ff00',
    borderRadius: 4,
    padding: 20,
    marginBottom: 20,
    shadowColor: '#00ff00',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.3,
    shadowRadius: 10,
    elevation: 5,
  },
  label: {
    fontSize: 14,
    color: '#55ff55',
    fontFamily: 'monospace',
    marginBottom: 5,
  },
  value: {
    fontSize: 22,
    color: '#00ff00',
    fontFamily: 'monospace',
    fontWeight: 'bold',
    marginBottom: 10,
  },
  colorGreen: {
    color: '#00ff00',
  },
  colorRed: {
    color: '#ff003c',
  },
  divider: {
    height: 1,
    backgroundColor: '#003300',
    marginVertical: 15,
  },
  buttonContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 10,
  },
  button: {
    backgroundColor: '#002200',
    borderWidth: 1,
    borderColor: '#00ff00',
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: 2,
    flex: 0.48,
    alignItems: 'center',
  },
  buttonText: {
    color: '#00ff00',
    fontFamily: 'monospace',
    fontSize: 16,
    fontWeight: 'bold',
  },
  cameraContainer: {
    flex: 1,
    backgroundColor: '#0a0a0a',
    borderWidth: 1,
    borderColor: '#005500',
    borderRadius: 4,
    padding: 15,
  },
  cameraPlaceholder: {
    flex: 1,
    backgroundColor: '#000',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#003300',
    borderStyle: 'dashed',
    marginTop: 10,
  },
  cameraText: {
    color: '#005500',
    fontFamily: 'monospace',
    fontSize: 12,
    textAlign: 'center',
  }
});