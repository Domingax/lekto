import { RouterProvider, createBrowserRouter } from 'react-router-dom'

function App({ router }: Readonly<{ router: ReturnType<typeof createBrowserRouter> }>) {
  return <RouterProvider router={router} />
}

export default App
