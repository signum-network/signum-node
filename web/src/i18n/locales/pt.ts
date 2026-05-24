import type { Locale } from './en'

const pt: Locale = {
  nav: {
    dashboard: 'Painel',
    network: 'Rede',
    blocks: 'Blocos',
    wallet: 'Carteira',
    soon: 'em breve',
  },
  common: {
    refresh: '↻ atualizar',
    refreshing: 'atualizando…',
    copy: 'Copiar',
    height: 'Altura',
    address: 'Endereço',
    failures: 'Falhas',
    cachedAgo: 'cache há {{seconds}}s',
    noData: 'Sem dados',
    searchAgain: 'Buscar novamente',
    startBinarySearch: 'Iniciar busca binária',
    searching: 'Buscando… pode levar alguns segundos',
    selectTheme: 'Selecionar tema',
    selectLanguage: 'Selecionar idioma',
  },
  status: {
    onChain: 'na cadeia',
    stale: 'desatualizado',
    forking: 'bifurcando',
    blacklisted: 'bloqueado',
  },
  dashboard: {
    chainActivity: 'Atividade da cadeia',
    network: 'Rede',
    nodeHealth: 'Saúde do nó',
  },
  network: {
    chainConsensus: 'Consenso da cadeia',
    peers: 'Pares',
    forkHistory: 'Histórico de bifurcações',
    blacklistRecommendations: 'Recomendações de bloqueio',
    agree: '{{on}} concordam · {{stale}} desatual. · {{forking}} bifurcando',
    onOurChain: 'na nossa cadeia',
    noReorgs: 'Nenhuma reorganização registrada desde o início do nó',
    noRecommendations: 'Sem recomendações no momento',
    noPeerData: 'Sem dados de pares — status atualiza a cada 120 segundos',
    blacklistHint: 'Adicione endereços a P2P.BlacklistedPeers em node.properties para bloqueá-los.',
    peersOpen: '{{count}} par ▲',
    peersOpen_other: '{{count}} pares ▲',
    peersClosed: '{{count}} par ▼',
    peersClosed_other: '{{count}} pares ▼',
    forkPoint: {
      title: 'Busca do ponto de bifurcação',
      findFork: 'Encontrar bifurcação',
      findForkFull: 'Encontrar ponto de bifurcação',
      forkHeight: 'Altura da bifurcação',
      forkBlockId: 'ID do bloco na bifurcação',
      ourBlock: 'Nosso bloco na bifurcação',
      steps: 'Passos da busca',
    },
    rollback: '-{{depth}} bloco',
    rollback_other: '-{{depth}} blocos',
  },
  info: {
    chainConsensus:
      'A porcentagem de pares conectados cuja dificuldade acumulada corresponde ou está dentro de 5 blocos da nossa. Valores ≥90% indicam que a rede está em forte acordo sobre a cadeia canônica.',
    cumulativeDifficulty:
      'O trabalho total de prova de compromisso investido na cadeia desde o genesis. A cadeia com maior dificuldade acumulada é sempre escolhida como canônica — tornando extremamente custoso reescrever o histórico.',
    findFork:
      'Realiza uma busca binária entre seu nó e o par selecionado para localizar a altura exata do bloco onde as cadeias divergiram. Útil para diagnosticar forks.',
    networkCapacity:
      'O compromisso de armazenamento total estimado (em TiB/PiB) em todos os mineradores ativos, derivado do alvo base atual. Maior capacidade significa mais segurança na rede.',
    baseTarget:
      'Ajustado a cada bloco para manter o tempo médio de bloco em 4 minutos. Um alvo base menor significa maior dificuldade — mais difícil ganhar a mineração.',
    peersTable:
      'Pares "na cadeia" compartilham nossa dificuldade acumulada exata. Os "desatualizados" estão dentro de 5 blocos. Os "bifurcando" estão em uma cadeia significativamente diferente.',
    blacklistRecommendations:
      'Pares que estão consistentemente em uma cadeia diferente ou têm falhas de conexão repetidas. Adicioná-los a P2P.BlacklistedPeers em node.properties evita que seu nó gaste recursos com eles.',
  },
}

export default pt
