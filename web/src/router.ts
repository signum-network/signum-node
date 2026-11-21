import { createRouter, createRootRoute, createRoute, createHashHistory, redirect, Navigate } from '@tanstack/react-router'
import RootLayout from './routes/__root'
import DashboardPage from './routes/index'
import NetworkPage from './routes/network/index'
import BlocksPage from './routes/blocks/index'
import BlockDetailPage from './routes/blocks/detail'
import TransactionPage from './routes/tx/detail'
import AccountPage from './routes/accounts/detail'
import { parseAccountTab, type AccountTab } from './components/account/account.utils'

const rootRoute = createRootRoute({
  component: RootLayout,
  notFoundComponent: () => Navigate({ to: '/' }),
})

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: DashboardPage,
})

const networkRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/network',
  component: NetworkPage,
})

const blocksRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/blocks',
  // `from`: height of the newest block on an older page; absent for the live page
  validateSearch: (search: Record<string, unknown>): { from?: number } => {
    const from = Number(search.from)
    return search.from !== undefined && Number.isSafeInteger(from) && from >= 0 ? { from } : {}
  },
  component: BlocksPage,
})

const blockDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/blocks/$height',
  component: BlockDetailPage,
})

const transactionRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/tx/$id',
  component: TransactionPage,
})

const accountRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/accounts/$id',
  // `tab` absent for the overview; unknown values are dropped
  validateSearch: (search: Record<string, unknown>): { tab?: Exclude<AccountTab, 'overview'> } => {
    const tab = parseAccountTab(search.tab)
    return tab === 'overview' ? {} : { tab }
  },
  component: AccountPage,
})

// Future routes — redirect to home until implemented

const walletRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/wallet',
  beforeLoad: () => { throw redirect({ to: '/' }) },
})

const catchAllRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '*',
  beforeLoad: () => { throw redirect({ to: '/' }) },
})

export const routeTree = rootRoute.addChildren([
  indexRoute,
  networkRoute,
  blocksRoute,
  blockDetailRoute,
  transactionRoute,
  accountRoute,
  walletRoute,
  catchAllRoute,
])

export const router = createRouter({
  routeTree,
  history: createHashHistory(),
  defaultPreload: 'intent',
})

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
