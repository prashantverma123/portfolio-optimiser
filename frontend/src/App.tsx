import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import './App.css'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

type UserRecord = {
  id: string
  firstName: string
  lastName: string
  email: string
}

function App() {
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [uploadMessage, setUploadMessage] = useState<string>('')
  const [errorMessage, setErrorMessage] = useState<string>('')
  const [users, setUsers] = useState<UserRecord[]>([])
  const [isUploading, setIsUploading] = useState(false)
  const [isRefreshing, setIsRefreshing] = useState(false)

  const fetchUsers = useCallback(async () => {
    setIsRefreshing(true)
    try {
      const response = await fetch(`${API_BASE_URL}/api/users`)
      if (!response.ok) {
        throw new Error('Failed to load processed users')
      }
      const data: UserRecord[] = await response.json()
      setUsers(data)
    } catch (error) {
      console.error(error)
    } finally {
      setIsRefreshing(false)
    }
  }, [])

  useEffect(() => {
    fetchUsers()
  }, [fetchUsers])

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setErrorMessage('')
    setUploadMessage('')

    if (!selectedFile) {
      setErrorMessage('Please select a CSV file before uploading.')
      return
    }

    if (!selectedFile.name.toLowerCase().endsWith('.csv')) {
      setErrorMessage('Only .csv files are supported.')
      return
    }

    const formData = new FormData()
    formData.append('file', selectedFile)
    setIsUploading(true)

    try {
      const response = await fetch(`${API_BASE_URL}/api/uploads`, {
        method: 'POST',
        body: formData,
      })

      const payload = await response.json().catch(() => ({ message: 'Upload finished' }))

      if (!response.ok) {
        throw new Error(payload.message ?? 'Upload failed')
      }

      setUploadMessage(payload.message ?? 'Upload accepted')
      setSelectedFile(null)
      await fetchUsers()
    } catch (error) {
      console.error(error)
      setErrorMessage(error instanceof Error ? error.message : 'Upload failed')
    } finally {
      setIsUploading(false)
    }
  }

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    setSelectedFile(file ?? null)
    setErrorMessage('')
  }

  const helperText = useMemo(
    () =>
      'Expected columns: id, firstName, lastName, email. For example:\n1,Alex,Miller,alex@example.com',
    []
  )

  return (
    <div className="app-shell">
      <header>
        <h1>CSV User Import</h1>
        <p>Upload a CSV file and let the backend process the users asynchronously.</p>
      </header>

      <section className="card">
        <h2>Upload</h2>
        <form onSubmit={handleSubmit} className="upload-form">
          <label className="file-input">
            <span>Select CSV File</span>
            <input type="file" accept=".csv" onChange={handleFileChange} />
          </label>
          <button type="submit" disabled={isUploading}>
            {isUploading ? 'Uploading…' : 'Start Import'}
          </button>
        </form>
        <p className="helper-text">{helperText}</p>
        {selectedFile && <p className="file-name">Selected: {selectedFile.name}</p>}
        {uploadMessage && <p className="status success">{uploadMessage}</p>}
        {errorMessage && <p className="status error">{errorMessage}</p>}
      </section>

      <section className="card">
        <div className="section-header">
          <h2>Processed Users</h2>
          <button type="button" onClick={fetchUsers} disabled={isRefreshing}>
            {isRefreshing ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>
        {users.length === 0 ? (
          <p className="empty-state">No user data has been processed yet.</p>
        ) : (
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>First name</th>
                  <th>Last name</th>
                  <th>Email</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr key={`${user.id}-${user.email}`}>
                    <td>{user.id}</td>
                    <td>{user.firstName}</td>
                    <td>{user.lastName}</td>
                    <td>{user.email}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}

export default App
