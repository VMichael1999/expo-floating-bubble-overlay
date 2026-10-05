import { Platform } from 'react-native';

type Fachada = typeof import('../index').BurbujaFlotante;

function nativoFalso() {
  return {
    tienePermiso: jest.fn(() => true),
    abrirAjustesPermiso: jest.fn(),
    mostrar: jest.fn(() => true),
    mantenerActiva: jest.fn(),
    soltarActiva: jest.fn(),
    ocultar: jest.fn(),
    abrirApp: jest.fn(() => true),
    programarApertura: jest.fn(),
    cancelarApertura: jest.fn(),
    estaVisible: jest.fn(() => true),
    addListener: jest.fn(() => ({ remove: jest.fn() })),
  };
}

/** Carga la fachada con la plataforma y el modulo nativo indicados. */
function cargar(os: typeof Platform.OS, nativo: ReturnType<typeof nativoFalso> | null): Fachada {
  let fachada!: Fachada;
  jest.isolateModules(() => {
    jest.doMock('expo', () => ({
      ...jest.requireActual('expo'),
      requireOptionalNativeModule: jest.fn(() => nativo),
    }));
    require('react-native').Platform.OS = os;
    fachada = require('../index').BurbujaFlotante;
  });
  return fachada;
}

const osOriginal = Platform.OS;

afterEach(() => {
  jest.dontMock('expo');
  Platform.OS = osOriginal;
});

describe('BurbujaFlotante sin modulo nativo', () => {
  it.each(['ios', 'web'] as const)('en %s es un no-op seguro', (os) => {
    const nativo = nativoFalso();
    const b = cargar(os, nativo);

    expect(b.disponible).toBe(false);
    expect(b.tienePermiso()).toBe(false);
    expect(b.mostrar()).toBe(false);
    expect(b.abrirApp()).toBe(false);
    expect(b.estaVisible()).toBe(false);
    expect(() => {
      b.ocultar();
      b.abrirAjustesPermiso();
      b.mantenerActiva();
      b.soltarActiva();
      b.programarApertura(4);
      b.cancelarApertura();
    }).not.toThrow();
    expect(b.alTocar(jest.fn()).remove).toEqual(expect.any(Function));
    expect(nativo.mostrar).not.toHaveBeenCalled();
  });

  it('en Android sin development build (Expo Go) es un no-op seguro', () => {
    const b = cargar('android', null);

    expect(b.disponible).toBe(false);
    expect(b.mostrar({ tamano: 60 })).toBe(false);
    expect(() => b.alCerrar(jest.fn()).remove()).not.toThrow();
  });
});

describe('BurbujaFlotante en Android', () => {
  it('delega cada llamada en el modulo nativo', () => {
    const nativo = nativoFalso();
    const b = cargar('android', nativo);
    const opciones = { tamano: 72, tituloNotificacion: 'Viaje en curso' };

    expect(b.disponible).toBe(true);
    expect(b.tienePermiso()).toBe(true);
    expect(b.mostrar(opciones)).toBe(true);
    expect(nativo.mostrar).toHaveBeenCalledWith(opciones);
    expect(b.abrirApp()).toBe(true);
    expect(b.estaVisible()).toBe(true);

    b.ocultar();
    b.abrirAjustesPermiso();
    b.mantenerActiva(opciones);
    b.soltarActiva();
    b.programarApertura(4);
    b.cancelarApertura();

    expect(nativo.ocultar).toHaveBeenCalled();
    expect(nativo.abrirAjustesPermiso).toHaveBeenCalled();
    expect(nativo.mantenerActiva).toHaveBeenCalledWith(opciones);
    expect(nativo.soltarActiva).toHaveBeenCalled();
    expect(nativo.programarApertura).toHaveBeenCalledWith(4);
    expect(nativo.cancelarApertura).toHaveBeenCalled();
  });

  it('mostrar sin opciones envia un objeto vacio', () => {
    const nativo = nativoFalso();
    cargar('android', nativo).mostrar();

    expect(nativo.mostrar).toHaveBeenCalledWith({});
  });

  it('suscribe los eventos onTocar y onCerrar', () => {
    const nativo = nativoFalso();
    const b = cargar('android', nativo);
    const alTocar = jest.fn();
    const alCerrar = jest.fn();

    b.alTocar(alTocar);
    b.alCerrar(alCerrar);

    expect(nativo.addListener).toHaveBeenCalledWith('onTocar', alTocar);
    expect(nativo.addListener).toHaveBeenCalledWith('onCerrar', alCerrar);
  });
});
