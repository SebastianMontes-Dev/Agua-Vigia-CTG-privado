import {
  bitacoraDeEjemplo,
  cumplimientoDeEjemplo,
  estadisticasDeEjemplo,
  serieDeEjemplo,
} from './historia'
import { AHORA_DE_EJEMPLO, sectoresDeEjemplo } from './sectores'

describe('datos de ejemplo', () => {
  describe('sectoresDeEjemplo', () => {
    const sectores = sectoresDeEjemplo.sectores

    it('debeIncluirAlMenosOchoSectoresDeBarriosReales', () => {
      expect(sectores.length).toBeGreaterThanOrEqual(8)
    })

    it('debeTenerAlMenosUnSectorConCadaEstadoPosible', () => {
      const estados = new Set(sectores.map((s) => s.estado))
      expect(estados.has('SIN_SERVICIO')).toBe(true)
      expect(estados.has('CORTE_PROGRAMADO')).toBe(true)
      expect(estados.has('PRESION_BAJA')).toBe(true)
      expect(estados.has('CON_SERVICIO')).toBe(true)
    })

    it('debeTenerAlMenosDosSectoresConEstadoNuloYFechasNulas', () => {
      const nulos = sectores.filter((s) => s.estado === null)
      expect(nulos.length).toBeGreaterThanOrEqual(2)
      for (const sector of nulos) {
        expect(sector.actualizadoEn).toBeNull()
        expect(sector.verificadoEn).toBeNull()
      }
    })

    it('debeTenerFechasNoNulasYVerificadoPosteriorOIgualAActualizadoCuandoEstadoNoEsNulo', () => {
      const conEstado = sectores.filter((s) => s.estado !== null)
      expect(conEstado.length).toBeGreaterThan(0)
      for (const sector of conEstado) {
        expect(sector.actualizadoEn).not.toBeNull()
        expect(sector.verificadoEn).not.toBeNull()
        const actualizadoMs = new Date(sector.actualizadoEn!).getTime()
        const verificadoMs = new Date(sector.verificadoEn!).getTime()
        expect(verificadoMs).toBeGreaterThanOrEqual(actualizadoMs)
      }
    })

    it('debeTenerUnSectorConVerificacionViejaYUnoRecienVerificadoConEstadoEstable', () => {
      const ahoraMs = AHORA_DE_EJEMPLO.getTime()
      const unDiaMs = 24 * 60 * 60 * 1000
      const unaHoraMs = 60 * 60 * 1000

      const masDe24h = sectores.filter((s) => {
        if (!s.verificadoEn) return false
        return ahoraMs - new Date(s.verificadoEn).getTime() > unDiaMs
      })
      expect(masDe24h.length).toBeGreaterThanOrEqual(1)

      const recienVerificadoEstable = sectores.filter((s) => {
        if (!s.verificadoEn || !s.actualizadoEn) return false
        const verificadoHaceMs = ahoraMs - new Date(s.verificadoEn).getTime()
        const actualizadoHaceMs = ahoraMs - new Date(s.actualizadoEn).getTime()
        return verificadoHaceMs < unaHoraMs && actualizadoHaceMs > 2 * unDiaMs
      })
      expect(recienVerificadoEstable.length).toBeGreaterThanOrEqual(1)
    })

    it('debeTenerAlMenosUnSectorConPoblacionNulaYNingunoConPoblacionCero', () => {
      const conPoblacionNula = sectores.filter((s) => s.poblacion === null)
      expect(conPoblacionNula.length).toBeGreaterThanOrEqual(1)

      for (const sector of sectores) {
        expect(sector.poblacion).not.toBe(0)
      }
    })

    it('debeTenerIdentificadoresUnicosYListaOrdenadaPorNombre', () => {
      const ids = sectores.map((s) => s.id)
      const idsUnicos = new Set(ids)
      expect(idsUnicos.size).toBe(sectores.length)

      const nombres = sectores.map((s) => s.nombre!)
      const nombresOrdenados = [...nombres].sort((a, b) => a.localeCompare(b))
      expect(nombres).toEqual(nombresOrdenados)
    })

    it('debeAsegurarQueNingunaFechaEsPosteriorAAhoraDeEjemplo', () => {
      const ahoraMs = AHORA_DE_EJEMPLO.getTime()
      expect(new Date(sectoresDeEjemplo.generadoEn).getTime()).toBeLessThanOrEqual(ahoraMs)

      const fechasActualizado = sectores.map((s) => s.actualizadoEn).filter(Boolean) as string[]
      for (const fecha of fechasActualizado) {
        expect(new Date(fecha).getTime()).toBeLessThanOrEqual(ahoraMs)
      }

      const fechasVerificado = sectores.map((s) => s.verificadoEn).filter(Boolean) as string[]
      for (const fecha of fechasVerificado) {
        expect(new Date(fecha).getTime()).toBeLessThanOrEqual(ahoraMs)
      }
    })
  })

  describe('historiaDeEjemplo', () => {
    it('debeTenerAlMenosSeisEventosOrdenadosDeMasRecienteAMasAntiguo', () => {
      expect(bitacoraDeEjemplo.length).toBeGreaterThanOrEqual(6)
      for (let i = 0; i < bitacoraDeEjemplo.length - 1; i++) {
        const actual = new Date(bitacoraDeEjemplo[i]!.timestamp!).getTime()
        const siguiente = new Date(bitacoraDeEjemplo[i + 1]!.timestamp!).getTime()
        expect(actual).toBeGreaterThanOrEqual(siguiente)
      }
    })

    it('debeCubrirLosCuatroTiposDeEventosDeBitacora', () => {
      const tipos = new Set(bitacoraDeEjemplo.map((e) => e.tipo))
      expect(tipos.has('CORTE_ANUNCIADO')).toBe(true)
      expect(tipos.has('CORTE_CONFIRMADO_POR_CIUDADANOS')).toBe(true)
      expect(tipos.has('CORTE_RESTABLECIDO')).toBe(true)
      expect(tipos.has('CORTE_DETECTADO_POR_INGESTA')).toBe(true)
    })

    it('debeExigirAlMenosTresReportesDeSustentoEnConsensoCiudadanoYCeroEnLosDemas', () => {
      const confirmados = bitacoraDeEjemplo.filter((e) => e.tipo === 'CORTE_CONFIRMADO_POR_CIUDADANOS')
      const otros = bitacoraDeEjemplo.filter((e) => e.tipo !== 'CORTE_CONFIRMADO_POR_CIUDADANOS')

      expect(confirmados.length).toBeGreaterThanOrEqual(1)
      for (const evento of confirmados) {
        expect(evento.cantidadReportesSustento).toBeGreaterThanOrEqual(3)
      }

      expect(otros.length).toBeGreaterThan(0)
      for (const evento of otros) {
        expect(evento.cantidadReportesSustento).toBe(0)
      }
    })

    it('debeTenerUrlsQueEmpiezanPorAcuacarYContienenEjemploEnEventosDeIngesta', () => {
      const ingesta = bitacoraDeEjemplo.filter((e) => e.tipo === 'CORTE_DETECTADO_POR_INGESTA')
      expect(ingesta.length).toBeGreaterThanOrEqual(1)
      for (const evento of ingesta) {
        expect(evento.urlOriginal?.startsWith('https://www.acuacar.com/')).toBe(true)
        expect(evento.urlOriginal?.includes('ejemplo')).toBe(true)
        expect(evento.imagenUrl?.startsWith('https://www.acuacar.com/')).toBe(true)
        expect(evento.imagenUrl?.includes('ejemplo')).toBe(true)
      }
    })

    it('debeTenerAlMenosUnEventoConEstadoNuloYUnoConSectorNulo', () => {
      const conEstadoNulo = bitacoraDeEjemplo.filter((e) => !e.estado)
      const conSectorNulo = bitacoraDeEjemplo.filter((e) => !e.sectorId)
      expect(conEstadoNulo.length).toBeGreaterThanOrEqual(1)
      expect(conSectorNulo.length).toBeGreaterThanOrEqual(1)
    })

    it('debeUsarIdentificadoresDeSectorQueExistenEnSectoresDeEjemplo', () => {
      const idsValidos = new Set(sectoresDeEjemplo.sectores.map((s) => s.id))
      const eventosConSector = bitacoraDeEjemplo.filter((e) => Boolean(e.sectorId))

      expect(eventosConSector.length).toBeGreaterThan(0)
      for (const evento of eventosConSector) {
        expect(idsValidos.has(evento.sectorId!)).toBe(true)
      }
    })

    it('debeCumplirLaRelacionDeDesviacionYDuracionEnElIndiceDeCumplimiento', () => {
      expect(cumplimientoDeEjemplo.duracionRealSegundos).toBeGreaterThan(
        cumplimientoDeEjemplo.duracionPrometidaSegundos!
      )
      expect(cumplimientoDeEjemplo.desviacionSegundos).toBe(
        cumplimientoDeEjemplo.duracionRealSegundos! - cumplimientoDeEjemplo.duracionPrometidaSegundos!
      )
      expect(cumplimientoDeEjemplo.porcentajeCumplimiento).toBeGreaterThanOrEqual(0)
      expect(cumplimientoDeEjemplo.porcentajeCumplimiento).toBeLessThanOrEqual(100)
    })

    it('debeTenerSeisMesesConsecutivosConFormatoValido', () => {
      expect(serieDeEjemplo.length).toBe(6)
      for (const punto of serieDeEjemplo) {
        expect(punto.periodo).toMatch(/^\d{4}-\d{2}$/)
        expect(punto.desviacionSegundos).toBe(
          punto.duracionRealSegundos! - punto.duracionPrometidaSegundos!
        )
      }
      for (let i = 0; i < serieDeEjemplo.length - 1; i++) {
        const puntoActual = serieDeEjemplo[i]!
        const puntoSiguiente = serieDeEjemplo[i + 1]!
        const partesActual = puntoActual.periodo!.split('-').map(Number)
        const partesSiguiente = puntoSiguiente.periodo!.split('-').map(Number)
        const aAno = partesActual[0]!
        const aMes = partesActual[1]!
        const sAno = partesSiguiente[0]!
        const sMes = partesSiguiente[1]!
        const diferenciaMeses = (sAno - aAno) * 12 + (sMes - aMes)
        expect(diferenciaMeses).toBe(1)
      }
    })

    it('debeIncluirUnMesConUnSoloCorteYUnMesCapadoEnCien', () => {
      const conUnCorte = serieDeEjemplo.filter((p) => p.cantidadCortes === 1)
      const capadoEnCien = serieDeEjemplo.filter((p) => p.porcentajeCumplimiento === 100)
      expect(conUnCorte.length).toBeGreaterThanOrEqual(1)
      expect(capadoEnCien.length).toBeGreaterThanOrEqual(1)
    })

    it('debeContenerLosSieteDiasDeLaSemanaConTildesCorrectasYAlMenosUnoConCero', () => {
      const diasEsperados = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']
      const mapaDias = estadisticasDeEjemplo.cortesPorDiaDeSemana
      const claves = Object.keys(mapaDias)

      expect(new Set(claves)).toEqual(new Set(diasEsperados))
      expect(Object.values(mapaDias).some((c) => c === 0)).toBe(true)
    })

    it('debeTenerLasClavesDeDiasDesordenadas', () => {
      const ordenNatural = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']
      const clavesActuales = Object.keys(estadisticasDeEjemplo.cortesPorDiaDeSemana)
      expect(clavesActuales).not.toEqual(ordenNatural)
    })
  })
})
