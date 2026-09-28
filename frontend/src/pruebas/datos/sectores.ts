import type { components } from '../../api/generado/esquema'

export const AHORA_DE_EJEMPLO = new Date('2026-09-25T20:00:00Z')

export const sectoresDeEjemplo = {
  generadoEn: '2026-09-25T19:59:00Z',
  sectores: [
    {
      id: 'bocagrande',
      nombre: 'BOCAGRANDE',
      poblacion: 12000,
      estado: 'CON_SERVICIO',
      actualizadoEn: '2026-09-20T10:00:00Z',
      verificadoEn: '2026-09-25T19:30:00Z',
    },
    {
      id: 'canapote',
      nombre: 'CANAPOTE',
      poblacion: 8500,
      estado: 'PRESION_BAJA',
      actualizadoEn: '2026-09-25T15:00:00Z',
      verificadoEn: '2026-09-25T18:00:00Z',
    },
    {
      id: 'castillogrande',
      nombre: 'CASTILLOGRANDE',
      poblacion: 7200,
      estado: null,
      actualizadoEn: null,
      verificadoEn: null,
    },
    {
      id: 'centro',
      nombre: 'CENTRO',
      poblacion: null,
      estado: 'CORTE_PROGRAMADO',
      actualizadoEn: '2026-09-25T08:00:00Z',
      verificadoEn: '2026-09-25T12:00:00Z',
    },
    {
      id: 'el-cabrero',
      nombre: 'EL CABRERO',
      poblacion: 4300,
      estado: null,
      actualizadoEn: null,
      verificadoEn: null,
    },
    {
      id: 'getsemani',
      nombre: 'GETSEMANI',
      poblacion: 3800,
      estado: 'SIN_SERVICIO',
      actualizadoEn: '2026-09-24T10:00:00Z',
      verificadoEn: '2026-09-24T18:00:00Z',
    },
    {
      id: 'manga',
      nombre: 'MANGA',
      poblacion: 16500,
      estado: 'CON_SERVICIO',
      actualizadoEn: '2026-09-25T10:00:00Z',
      verificadoEn: '2026-09-25T10:00:00Z',
    },
    {
      id: 'torices',
      nombre: 'TORICES',
      poblacion: 19000,
      estado: 'SIN_SERVICIO',
      actualizadoEn: '2026-09-25T16:00:00Z',
      verificadoEn: '2026-09-25T17:30:00Z',
    },
  ],
} satisfies components['schemas']['RespuestaSectores']
