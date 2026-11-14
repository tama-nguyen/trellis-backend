import { SearchX } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { ROUTES } from "@/shared/constants/routes";
import { Button } from "@/ui/Button";
import { EmptyState } from "@/ui/EmptyState";
import { PageShell } from "@/ui/PageShell";

export default function NotFound() {
  const t = useTranslations("common");
  return (
    <PageShell>
      <EmptyState
        icon={SearchX}
        title={t("states.notFoundTitle")}
        description={t("states.notFoundDescription")}
        action={
          <Button asChild variant="secondary" size="sm">
            <Link href={ROUTES.home}>{t("nav.home")}</Link>
          </Button>
        }
      />
    </PageShell>
  );
}
