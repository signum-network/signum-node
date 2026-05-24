import type { Locale } from './en'

const es: Locale = {
  nav: {
    dashboard: 'Panel',
    network: 'Red',
    blocks: 'Bloques',
    wallet: 'Billetera',
    soon: 'pronto',
  },
  common: {
    refresh: '↻ actualizar',
    refreshing: 'actualizando…',
    copy: 'Copiar',
    height: 'Altura',
    address: 'Dirección',
    failures: 'Fallos',
    cachedAgo: 'caché hace {{seconds}}s',
    noData: 'Sin datos',
    searchAgain: 'Buscar de nuevo',
    startBinarySearch: 'Iniciar búsqueda binaria',
    searching: 'Buscando… puede tardar unos segundos',
    selectTheme: 'Seleccionar tema',
    selectLanguage: 'Seleccionar idioma',
  },
  status: {
    onChain: 'en cadena',
    stale: 'desactualizado',
    forking: 'bifurcando',
    blacklisted: 'bloqueado',
  },
  dashboard: {
    chainActivity: 'Actividad de cadena',
    network: 'Red',
    nodeHealth: 'Salud del nodo',
  },
  network: {
    chainConsensus: 'Consenso de cadena',
    peers: 'Pares',
    forkHistory: 'Historial de bifurcaciones',
    blacklistRecommendations: 'Recomendaciones de bloqueo',
    agree: '{{on}} acuerdo · {{stale}} desact. · {{forking}} bifurcando',
    onOurChain: 'en nuestra cadena',
    noReorgs: 'Sin reorganizaciones desde el inicio del nodo',
    noRecommendations: 'Sin recomendaciones por ahora',
    noPeerData: 'Sin datos de pares — el estado se actualiza cada 120 segundos',
    blacklistHint: 'Agrega direcciones a P2P.BlacklistedPeers en node.properties para bloquearlos.',
    peersOpen: '{{count}} par ▲',
    peersOpen_other: '{{count}} pares ▲',
    peersClosed: '{{count}} par ▼',
    peersClosed_other: '{{count}} pares ▼',
    forkPoint: {
      title: 'Búsqueda de punto de bifurcación',
      findFork: 'Encontrar bifurcación',
      findForkFull: 'Encontrar punto de bifurcación',
      forkHeight: 'Altura de la bifurcación',
      forkBlockId: 'ID de bloque en bifurcación',
      ourBlock: 'Nuestro bloque en bifurcación',
      steps: 'Pasos de búsqueda',
    },
    rollback: '-{{depth}} bloque',
    rollback_other: '-{{depth}} bloques',
  },
  info: {
    chainConsensus:
      'El porcentaje de pares conectados cuya dificultad acumulada coincide o está dentro de 5 bloques de la nuestra. Valores ≥90% indican que la red está en fuerte acuerdo sobre la cadena canónica.',
    cumulativeDifficulty:
      'El trabajo total de prueba de compromiso invertido en la cadena desde el génesis. La cadena con mayor dificultad acumulada siempre se elige como canónica — haciendo extremadamente costoso reescribir el historial.',
    findFork:
      'Realiza una búsqueda binaria entre tu nodo y el par seleccionado para localizar la altura exacta del bloque donde divergieron las cadenas. Útil para diagnosticar bifurcaciones.',
    networkCapacity:
      'El compromiso de almacenamiento total estimado (en TiB/PiB) en todos los mineros activos, derivado del objetivo base actual. Mayor capacidad significa mayor seguridad en la red.',
    baseTarget:
      'Se ajusta en cada bloque para mantener el tiempo promedio de bloque en 4 minutos. Un objetivo base más bajo significa mayor dificultad — más difícil ganar la minería.',
    peersTable:
      'Los pares "en cadena" comparten nuestra dificultad acumulada exacta. Los "desactualizados" están dentro de 5 bloques. Los "bifurcando" están en una cadena significativamente diferente.',
    blacklistRecommendations:
      'Pares que están consistentemente en una cadena diferente o tienen fallos de conexión repetidos. Agregarlos a P2P.BlacklistedPeers en node.properties evita que tu nodo gaste recursos en ellos.',
  },
}

export default es
