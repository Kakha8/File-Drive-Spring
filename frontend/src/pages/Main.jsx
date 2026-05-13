import { logout } from "../api/auth";

function Main({ onLogout }) {
    async function handleLogout() {
        await logout();
        onLogout();
    }

    return (
        <main className="main-page">
            <header className="top-bar">
                <div>
                    <h1>File Drive</h1>
                    <p>Your secure file dashboard</p>
                </div>

                <button className="logout-button" onClick={handleLogout}>
                    Logout
                </button>
            </header>

            <section className="dashboard">
                <div className="dashboard-card">
                    <h2>Welcome back</h2>
                    <p>You are logged in.</p>
                </div>

                <div className="dashboard-card">
                    <h2>Files</h2>
                    <p>Your files will appear here later.</p>
                </div>

                <div className="dashboard-card">
                    <h2>Folders</h2>
                    <p>Your folders will appear here later.</p>
                </div>
            </section>
        </main>
    );
}

export default Main;