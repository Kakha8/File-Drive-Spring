import { useEffect, useState } from "react";
import { refresh } from "./api/auth";
import { getAccessToken, subscribeAccessToken } from "./api/tokenStore";
import Login from "./pages/Login";
import Main from "./pages/Main";
import "./App.css";

function App() {
  const [checkingAuth, setCheckingAuth] = useState(true);
  const [isAuthed, setIsAuthed] = useState(false);

  useEffect(() => {
    async function restoreSession() {
      try {
        await refresh();
        setIsAuthed(Boolean(getAccessToken()));
      } catch {
        setIsAuthed(false);
      } finally {
        setCheckingAuth(false);
      }
    }

    restoreSession();

    return subscribeAccessToken((token) => {
      setIsAuthed(Boolean(token));
    });
  }, []);

  if (checkingAuth) {
    return (
        <main className="page">
          <p className="loading-text">Checking session...</p>
        </main>
    );
  }

  if (!isAuthed) {
    return <Login onLogin={() => setIsAuthed(Boolean(getAccessToken()))} />;
  }

  return <Main onLogout={() => setIsAuthed(false)} />;
}

export default App;