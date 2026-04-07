import { Link } from "react-router-dom";

export default function AppFooter() {
  return (
    <footer className="mozhi-footer">
      <div className="mozhi-container">
        <div className="mozhi-footer-inner">
          <span className="mozhi-footer-text">© 2026 MOZhi. Built for content, knowledge, community, and commerce.</span>
          <div className="mozhi-footer-links">
            <a href="https://github.com/Atingaii/MOZhi" rel="noreferrer" target="_blank">
              GitHub
            </a>
            <Link to="/search">Search</Link>
            <Link to="/profile">Profile</Link>
            <Link to="/settings">Settings</Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
