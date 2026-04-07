import { createBrowserRouter } from "react-router-dom";

import AuthLayout from "@/layouts/AuthLayout";
import AppShell from "@/layouts/AppShell";
import AuthPage from "@/pages/Auth";
import CommercePage from "@/pages/Commerce";
import EditorPage from "@/pages/Editor";
import FollowingPage from "@/pages/Following";
import HomePage from "@/pages/Home";
import NotificationsPage from "@/pages/Notifications";
import ProfilePage from "@/pages/Profile";
import QAPage from "@/pages/QA";
import SearchPage from "@/pages/Search";
import SettingsPage from "@/pages/Settings";
import { GuestRoute, ProtectedRoute } from "@/router/guards";

export const router = createBrowserRouter([
  {
    path: "/auth",
    element: <AuthLayout />,
    children: [
      {
        element: <GuestRoute />,
        children: [{ index: true, element: <AuthPage /> }]
      }
    ]
  },
  {
    path: "/",
    element: <AppShell />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "qa", element: <QAPage /> },
      { path: "search", element: <SearchPage /> },
      { path: "commerce", element: <CommercePage /> },
      {
        element: <ProtectedRoute />,
        children: [
          { path: "following", element: <FollowingPage /> },
          { path: "editor", element: <EditorPage /> },
          { path: "notifications", element: <NotificationsPage /> },
          { path: "profile", element: <ProfilePage /> },
          { path: "settings", element: <SettingsPage /> }
        ]
      }
    ]
  }
]);
