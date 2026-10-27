"""Conservative device impact selection; shared by local and hosted execution."""
MODULES = ("data", "design-system", "app", "catalog")
DEPENDENTS = {
    "domain": ("data", "app"),
    "data": ("data", "app"),
    "design-system": ("design-system", "app", "catalog"),
    "app": ("app",),
    "catalog": ("catalog",),
}
DOCUMENT_SUFFIXES = (".md", ".txt", ".html", ".css", ".svg", ".png", ".jpg", ".jpeg", ".webp", ".csv", ".json")


def is_document(path):
    return ((path.startswith("docs/") and path.endswith(DOCUMENT_SUFFIXES))
            or ("/" not in path and path.endswith(".md")))


def coverage(base, paths=(), gate="G2", full=False):
    paths = sorted(set(paths))
    result = {"mode": "full", "base": base, "changed_paths": paths, "device_modules": []}
    if gate == "G0":
        if any(not is_document(path) for path in paths):
            raise ValueError("G0 requires documentation-only inputs")
        return dict(result, mode="documents")
    if gate == "G3":
        # A release candidate is verified whole, whatever changed.
        return dict(result, device_modules=list(MODULES))
    if gate != "G2":
        return result
    if full:
        return dict(result, device_modules=list(MODULES))
    impacted = set()
    for path in paths:
        if is_document(path):
            continue
        module, _, relative = path.partition("/")
        if module not in DEPENDENTS or not (relative.startswith("src/") or (module == "data" and relative.startswith("schemas/"))):
            return dict(result, device_modules=list(MODULES))
        impacted.update(DEPENDENTS[module])
    # No module changes cannot establish affected device coverage (e.g. ticket advancement).
    if not impacted:
        return dict(result, device_modules=list(MODULES))
    return dict(result, mode="affected", device_modules=[m for m in MODULES if m in impacted])


# Hosted device groups and the modules each one runs (gates.DEVICE_GROUPS holds the tasks).
GROUPS = {"app-1": ("app",), "app-2": ("app",), "support": ("data", "design-system", "catalog")}


def groups(scope):
    modules = scope["device_modules"]
    return [group for group, covered in GROUPS.items() if any(module in modules for module in covered)]


def problem(scope, gate, ticket):
    modules = scope["device_modules"]
    if modules != [m for m in MODULES if m in modules] or len(modules) != len(set(modules)):
        return "unknown, duplicate or unordered device modules"
    if scope["changed_paths"] != sorted(set(scope["changed_paths"])):
        return "changed paths must be sorted and unique"
    if gate == "G0":
        if ticket != "UNTICKETED" or scope["mode"] != "documents" or modules or any(not is_document(p) for p in scope["changed_paths"]):
            return "documentation coverage cannot satisfy ticket or code verification"
    elif gate == "G2":
        if not modules or scope["mode"] not in ("full", "affected"):
            return "device coverage is required for G2"
        expected = coverage(scope["base"], scope["changed_paths"], full=scope["mode"] == "full")
        if scope != expected:
            return "device modules do not cover the dependency closure of changed paths"
    elif gate == "G3":
        if scope["mode"] != "full" or modules != list(MODULES):
            return "G3 needs every device module"
    elif scope["mode"] != "full" or modules:
        return "host coverage must be full without device modules"
    return None
