import { createRouter, createRootRoute, createRoute, createHashHistory, redirect, Navigate } from '@tanstack/react-router'
import RootLayout from './routes/__root'
import DashboardPage from './routes/index'
import NetworkPage from './routes/network/index'
import BlocksPage from './routes/blocks/index'

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

// detail pages follow; until then they lead back to the list
const blockDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/blocks/$height',
  beforeLoad: () => { throw redirect({ to: '/blocks', search: { page: 0 } }) },
})

const transactionRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/tx/$id',
  beforeLoad: () => { throw redirect({ to: '/blocks', search: { page: 0 } }) },
})

const accountRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/accounts/$id',
  beforeLoad: () => { throw redirect({ to: '/blocks', search: { page: 0 } }) },
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
