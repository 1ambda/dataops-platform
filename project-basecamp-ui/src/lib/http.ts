import ky from 'ky'

const http = ky.create({
  prefixUrl: 'http://localhost:5173/api',
  hooks: {
    beforeRequest: [
      () => {
        // const encodedCredentials = sessionStorage.getItem(ENCODED_CREDENTIALS)
        // request.headers.set('Authorization', `Basic ${encodedCredentials}`)
      },
    ],
  },
})

export { http }
