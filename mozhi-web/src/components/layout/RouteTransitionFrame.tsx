import { type ReactNode } from "react";
import { useLocation } from "react-router-dom";

interface RouteTransitionFrameProps {
  children: ReactNode;
}

export default function RouteTransitionFrame({ children }: RouteTransitionFrameProps) {
  const location = useLocation();
  const routeKey = location.pathname;

  return (
    <div className="mozhi-route-transition" data-route-key={routeKey} key={routeKey}>
      {children}
    </div>
  );
}
