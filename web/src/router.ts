import { createRouter, createRootRoute, createRoute, createHashHistory, redirect, Navigate } from '@tanstack/react-router'
import RootLayout from './routes/__root'
import DashboardPage from './routes/index'
import NetworkPage from './routes/network/index'
import BlocksPage from './routes/blocks/index'
import BlockDetailPage from './routes/blocks/detail'
import TransactionPage from './routes/tx/detail'
import AccountPage from './routes/accounts/detail'

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
  validateSearch: (search: Record<string, unknown>): { page: number } => {
    const page = Number(search.page)
    return { page: Number.isSafeInteger(page) && page > 0 ? page : 0 }
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
