import { jestConfig } from '../../jest.shared.mjs'

// The server side of sign in runs on node, not in a browser.
export default jestConfig({ testEnvironment: 'node' })
