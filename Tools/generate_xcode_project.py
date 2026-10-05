#!/usr/bin/env python3
"""Generates Zammad.xcodeproj/project.pbxproj from the sources on disk.

Run it after adding or removing files:

    python Tools/generate_xcode_project.py
"""

import hashlib
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE_DIR = os.path.join(ROOT, "Zammad")
SUPPORT_DIR = os.path.join(ROOT, "Support")
OUTPUT_DIR = os.path.join(ROOT, "Zammad.xcodeproj")
OUTPUT_FILE = os.path.join(OUTPUT_DIR, "project.pbxproj")

PROJECT_NAME = "Zammad"
BUNDLE_ID = "com.example.zammad.ios"
DEPLOYMENT_TARGET = "17.0"
MARKETING_VERSION = "1.0"
CURRENT_PROJECT_VERSION = "1"

SOURCE_EXTENSIONS = {".swift"}
RESOURCE_EXTENSIONS = {".xcassets", ".storyboard", ".xib", ".png", ".jpg", ".jpeg", ".json", ".strings"}
IGNORED_NAMES = {".DS_Store"}

IDS = {
    "project": "0A0000000000000000000001",
    "main_group": "0A0000000000000000000002",
    "products_group": "0A0000000000000000000003",
    "product_ref": "0A0000000000000000000004",
    "target": "0A0000000000000000000005",
    "sources_phase": "0A0000000000000000000006",
    "frameworks_phase": "0A0000000000000000000007",
    "resources_phase": "0A0000000000000000000008",
    "target_config_list": "0A0000000000000000000009",
    "project_config_list": "0A000000000000000000000A",
    "project_debug": "0A000000000000000000000B",
    "project_release": "0A000000000000000000000C",
    "target_debug": "0A000000000000000000000D",
    "target_release": "0A000000000000000000000E",
    "source_group": "0A000000000000000000000F",
    "support_group": "0A0000000000000000000010",
    "info_plist": "0A0000000000000000000011",
}


def object_id(*parts):
    key = ":".join(parts)
    return hashlib.md5(key.encode("utf-8")).hexdigest()[:24].upper()


def quote(value):
    if re.fullmatch(r"[A-Za-z0-9_./+-]+", value):
        return value
    return '"%s"' % value.replace("\\", "\\\\").replace('"', '\\"')


def file_type(name):
    ext = os.path.splitext(name)[1].lower()
    if ext in SOURCE_EXTENSIONS:
        return "sourcecode.swift"
    if ext == ".xcassets":
        return "folder.assetcatalog"
    if ext == ".plist":
        return "text.plist.xml"
    if ext == ".json":
        return "text.json"
    if ext in {".png", ".jpg", ".jpeg", ".gif"}:
        return "image.png"
    if ext == ".md":
        return "net.daringfireball.markdown"
    return "text"


def build_phase(name):
    ext = os.path.splitext(name)[1].lower()
    if ext in SOURCE_EXTENSIONS:
        return "Sources"
    if ext in RESOURCE_EXTENSIONS:
        return "Resources"
    return None


class Node:
    def __init__(self, name, relpath, directory=False):
        self.name = name
        self.relpath = relpath
        self.directory = directory
        self.children = []
        self.identifier = object_id("id", relpath)


def scan(directory, prefix=""):
    nodes = []
    for name in sorted(os.listdir(directory)):
        if name in IGNORED_NAMES or name.startswith("."):
            continue
        full = os.path.join(directory, name)
        relpath = (prefix + "/" + name) if prefix else name
        if os.path.isdir(full) and not name.endswith(".xcassets"):
            node = Node(name, relpath, directory=True)
            node.children = scan(full, relpath)
            nodes.append(node)
        else:
            nodes.append(Node(name, relpath, directory=False))
    return nodes


class Project:
    def __init__(self):
        self.file_references = []
        self.build_files = []
        self.groups = []
        self.sources = []
        self.resources = []

    def add_node(self, node, refs):
        refs.append("\t\t\t%s /* %s */," % (node.identifier, node.name))

        if node.directory:
            child_refs = []
            for child in node.children:
                self.add_node(child, child_refs)
            self.groups.append(group_block(node, child_refs))
            return

        self.file_references.append(
            '\t%s /* %s */ = {isa = PBXFileReference; lastKnownFileType = %s; '
            'path = %s; sourceTree = "<group>"; };'
            % (node.identifier, node.name, file_type(node.name), quote(node.name))
        )

        phase = build_phase(node.name)
        if phase == "Sources":
            self.sources.append(node)
        elif phase == "Resources":
            self.resources.append(node)

        if phase:
            build_id = object_id("build", node.relpath)
            self.build_files.append(
                "\t%s /* %s in %s */ = {isa = PBXBuildFile; fileRef = %s /* %s */; };"
                % (build_id, node.name, phase, node.identifier, node.name)
            )


def group_block(node, child_refs):
    lines = [
        "\t%s /* %s */ = {" % (node.identifier, node.name),
        "\t\tisa = PBXGroup;",
        "\t\tchildren = (",
    ]
    lines.extend(child_refs)
    lines.extend([
        "\t\t);",
        "\t\tpath = %s;" % quote(node.name),
        '\t\tsourceTree = "<group>";',
        "\t};",
    ])
    return "\n".join(lines)


def render_settings(identifier, name, values):
    lines = [
        "\t%s /* %s */ = {" % (identifier, name),
        "\t\tisa = XCBuildConfiguration;",
        "\t\tbuildSettings = {",
    ]
    for key in sorted(values):
        lines.append("\t\t\t%s = %s;" % (key, values[key]))
    lines.extend([
        "\t\t};",
        "\t\tname = %s;" % name,
        "\t};",
    ])
    return "\n".join(lines)


def target_settings(debug):
    values = {
        "ASSETCATALOG_COMPILER_APPICON_NAME": "AppIcon",
        "ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME": "AccentColor",
        "CODE_SIGN_STYLE": "Automatic",
        "CURRENT_PROJECT_VERSION": CURRENT_PROJECT_VERSION,
        "ENABLE_PREVIEWS": "YES",
        "GENERATE_INFOPLIST_FILE": "NO",
        "INFOPLIST_FILE": "Support/Info.plist",
        "IPHONEOS_DEPLOYMENT_TARGET": DEPLOYMENT_TARGET,
        "LD_RUNPATH_SEARCH_PATHS": '"$(inherited) @executable_path/Frameworks"',
        "MARKETING_VERSION": MARKETING_VERSION,
        "PRODUCT_BUNDLE_IDENTIFIER": BUNDLE_ID,
        "PRODUCT_NAME": '"$(TARGET_NAME)"',
        "SWIFT_VERSION": "5.0",
        "TARGETED_DEVICE_FAMILY": '"1,2"',
    }
    if debug:
        values["SWIFT_ACTIVE_COMPILATION_CONDITIONS"] = '"DEBUG $(inherited)"'
        values["SWIFT_OPTIMIZATION_LEVEL"] = '"-Onone"'
    else:
        values["SWIFT_COMPILATION_MODE"] = "wholemodule"
        values["SWIFT_OPTIMIZATION_LEVEL"] = '"-O"'
    return values


def project_settings(debug):
    values = {
        "ALWAYS_SEARCH_USER_PATHS": "NO",
        "CLANG_ANALYZER_NONNULL": "YES",
        "CLANG_ENABLE_MODULES": "YES",
        "CLANG_ENABLE_OBJC_ARC": "YES",
        "CLANG_WARN_BOOL_CONVERSION": "YES",
        "CLANG_WARN_CONSTANT_CONVERSION": "YES",
        "CLANG_WARN_DEPRECATED_OBJC_IMPLEMENTATIONS": "YES",
        "CLANG_WARN_EMPTY_BODY": "YES",
        "CLANG_WARN_ENUM_CONVERSION": "YES",
        "CLANG_WARN_INT_CONVERSION": "YES",
        "CLANG_WARN_OBJC_LITERAL_CONVERSION": "YES",
        "CLANG_WARN_RANGE_LOOP_ANALYSIS": "YES",
        "CLANG_WARN_STRICT_PROTOTYPES": "YES",
        "CLANG_WARN_UNREACHABLE_CODE": "YES",
        "CLANG_WARN__DUPLICATE_METHOD_MATCH": "YES",
        "COPY_PHASE_STRIP": "NO",
        "ENABLE_STRICT_OBJC_MSGSEND": "YES",
        "ENABLE_USER_SCRIPT_SANDBOXING": "YES",
        "GCC_C_LANGUAGE_STANDARD": "gnu17",
        "GCC_NO_COMMON_BLOCKS": "YES",
        "IPHONEOS_DEPLOYMENT_TARGET": DEPLOYMENT_TARGET,
        "SDKROOT": "iphoneos",
        "SWIFT_VERSION": "5.0",
    }
    if debug:
        values.update({
            "DEBUG_INFORMATION_FORMAT": "dwarf",
            "ENABLE_TESTABILITY": "YES",
            "GCC_DYNAMIC_NO_PIC": "NO",
            "GCC_OPTIMIZATION_LEVEL": "0",
            "GCC_PREPROCESSOR_DEFINITIONS": '"$(inherited) DEBUG=1"',
            "ONLY_ACTIVE_ARCH": "YES",
            "SWIFT_ACTIVE_COMPILATION_CONDITIONS": '"DEBUG $(inherited)"',
            "SWIFT_OPTIMIZATION_LEVEL": '"-Onone"',
        })
    else:
        values.update({
            "DEBUG_INFORMATION_FORMAT": '"dwarf-with-dsym"',
            "ENABLE_NS_ASSERTIONS": "NO",
            "SWIFT_COMPILATION_MODE": "wholemodule",
            "SWIFT_OPTIMIZATION_LEVEL": '"-O"',
            "VALIDATE_PRODUCT": "YES",
        })
    return values


def main():
    if not os.path.isdir(SOURCE_DIR):
        print("Source folder not found: %s" % SOURCE_DIR)
        raise SystemExit(1)

    project = Project()

    # Source folder (mirrors the directory structure).
    source_root = Node(PROJECT_NAME, "")
    source_root.identifier = IDS["source_group"]
    source_root.directory = True
    source_root.children = scan(SOURCE_DIR)
    source_root_refs = []
    for child in source_root.children:
        project.add_node(child, source_root_refs)

    # Support folder (Info.plist is only referenced by the build settings).
    support_refs = []
    support_node = Node("Support", "Support", directory=True)
    support_node.identifier = IDS["support_group"]
    if os.path.isdir(SUPPORT_DIR):
        for name in sorted(os.listdir(SUPPORT_DIR)):
            if name in IGNORED_NAMES or name.startswith("."):
                continue
            identifier = IDS["info_plist"] if name == "Info.plist" else object_id("id", "Support/" + name)
            node = Node(name, "Support/" + name)
            node.identifier = identifier
            project.file_references.append(
                '\t%s /* %s */ = {isa = PBXFileReference; lastKnownFileType = text.plist.xml; '
                'path = %s; sourceTree = "<group>"; };'
                % (identifier, name, quote(name))
            )
            support_refs.append("\t\t\t%s /* %s */," % (identifier, name))

    def refs_block(refs):
        return refs

    source_group_block = "\n".join([
        "\t%s /* %s */ = {" % (IDS["source_group"], PROJECT_NAME),
        "\t\tisa = PBXGroup;",
        "\t\tchildren = (",
    ] + source_root_refs + [
        "\t\t);",
        "\t\tpath = %s;" % PROJECT_NAME,
        '\t\tsourceTree = "<group>";',
        "\t};",
    ])

    support_group_block = "\n".join([
        "\t%s /* Support */ = {" % IDS["support_group"],
        "\t\tisa = PBXGroup;",
        "\t\tchildren = (",
    ] + support_refs + [
        "\t\t);",
        "\t\tpath = Support;",
        '\t\tsourceTree = "<group>";',
        "\t};",
    ])

    products_group_block = "\n".join([
        "\t%s /* Products */ = {" % IDS["products_group"],
        "\t\tisa = PBXGroup;",
        "\t\tchildren = (",
        "\t\t\t%s /* %s.app */," % (IDS["product_ref"], PROJECT_NAME),
        "\t\t);",
        "\t\tname = Products;",
        '\t\tsourceTree = "<group>";',
        "\t};",
    ])

    main_group_block = "\n".join([
        "\t%s = {" % IDS["main_group"],
        "\t\tisa = PBXGroup;",
        "\t\tchildren = (",
        "\t\t\t%s /* %s */," % (IDS["source_group"], PROJECT_NAME),
        "\t\t\t%s /* Support */," % IDS["support_group"],
        "\t\t\t%s /* Products */," % IDS["products_group"],
        "\t\t);",
        '\t\tsourceTree = "<group>";',
        "\t};",
    ])

    def phase_entries(nodes, phase_name):
        entries = []
        for node in sorted(nodes, key=lambda item: item.relpath):
            build_id = object_id("build", node.relpath)
            entries.append("\t\t\t%s /* %s in %s */," % (build_id, node.name, phase_name))
        return entries

    parts = []
    parts.append("// !$*UTF8*$!")
    parts.append("{")
    parts.append("\tarchiveVersion = 1;")
    parts.append("\tclasses = {")
    parts.append("\t};")
    parts.append("\tobjectVersion = 56;")
    parts.append("\tobjects = {")
    parts.append("")

    parts.append("/* Begin PBXBuildFile section */")
    for line in sorted(set(project.build_files)):
        parts.append(line)
    parts.append("/* End PBXBuildFile section */")
    parts.append("")

    parts.append("/* Begin PBXFileReference section */")
    parts.append(
        '\t%s /* %s.app */ = {isa = PBXFileReference; explicitFileType = wrapper.application; '
        'includeInIndex = 0; path = %s.app; sourceTree = BUILT_PRODUCTS_DIR; };'
        % (IDS["product_ref"], PROJECT_NAME, PROJECT_NAME)
    )
    for line in sorted(set(project.file_references)):
        parts.append(line)
    parts.append("/* End PBXFileReference section */")
    parts.append("")

    parts.append("/* Begin PBXFrameworksBuildPhase section */")
    parts.append("\t%s /* Frameworks */ = {" % IDS["frameworks_phase"])
    parts.append("\t\tisa = PBXFrameworksBuildPhase;")
    parts.append("\t\tbuildActionMask = 2147483647;")
    parts.append("\t\tfiles = (")
    parts.append("\t\t);")
    parts.append("\t\trunOnlyForDeploymentPostprocessing = 0;")
    parts.append("\t};")
    parts.append("/* End PBXFrameworksBuildPhase section */")
    parts.append("")

    parts.append("/* Begin PBXGroup section */")
    parts.append(main_group_block)
    parts.append(products_group_block)
    parts.append(source_group_block)
    parts.append(support_group_block)
    for block in sorted(project.groups):
        parts.append(block)
    parts.append("/* End PBXGroup section */")
    parts.append("")

    parts.append("/* Begin PBXNativeTarget section */")
    parts.append("\t%s /* %s */ = {" % (IDS["target"], PROJECT_NAME))
    parts.append("\t\tisa = PBXNativeTarget;")
    parts.append(
        '\t\tbuildConfigurationList = %s /* Build configuration list for PBXNativeTarget "%s" */;'
        % (IDS["target_config_list"], PROJECT_NAME)
    )
    parts.append("\t\tbuildPhases = (")
    parts.append("\t\t\t%s /* Sources */," % IDS["sources_phase"])
    parts.append("\t\t\t%s /* Frameworks */," % IDS["frameworks_phase"])
    parts.append("\t\t\t%s /* Resources */," % IDS["resources_phase"])
    parts.append("\t\t);")
    parts.append("\t\tbuildRules = ();")
    parts.append("\t\tdependencies = ();")
    parts.append("\t\tname = %s;" % PROJECT_NAME)
    parts.append("\t\tproductName = %s;" % PROJECT_NAME)
    parts.append("\t\tproductReference = %s /* %s.app */;" % (IDS["product_ref"], PROJECT_NAME))
    parts.append('\t\tproductType = "com.apple.product-type.application";')
    parts.append("\t};")
    parts.append("/* End PBXNativeTarget section */")
    parts.append("")

    parts.append("/* Begin PBXProject section */")
    parts.append("\t%s /* Project object */ = {" % IDS["project"])
    parts.append("\t\tisa = PBXProject;")
    parts.append("\t\tattributes = {")
    parts.append("\t\t\tBuildIndependentTargetsInParallel = 1;")
    parts.append("\t\t\tLastSwiftUpdateCheck = 1600;")
    parts.append("\t\t\tLastUpgradeCheck = 1600;")
    parts.append("\t\t\tTargetAttributes = {")
    parts.append("\t\t\t\t%s = {" % IDS["target"])
    parts.append("\t\t\t\t\tCreatedOnToolsVersion = 16.0;")
    parts.append("\t\t\t\t};")
    parts.append("\t\t\t};")
    parts.append("\t\t};")
    parts.append(
        '\t\tbuildConfigurationList = %s /* Build configuration list for PBXProject "%s" */;'
        % (IDS["project_config_list"], PROJECT_NAME)
    )
    parts.append('\t\tcompatibilityVersion = "Xcode 14.0";')
    parts.append("\t\tdevelopmentRegion = en;")
    parts.append("\t\thasScannedForEncodings = 0;")
    parts.append("\t\tknownRegions = (")
    parts.append("\t\t\ten,")
    parts.append("\t\t\tru,")
    parts.append("\t\t\tBase,")
    parts.append("\t\t);")
    parts.append("\t\tmainGroup = %s;" % IDS["main_group"])
    parts.append("\t\tproductRefGroup = %s /* Products */;" % IDS["products_group"])
    parts.append('\t\tprojectDirPath = "";')
    parts.append('\t\tprojectRoot = "";')
    parts.append("\t\ttargets = (")
    parts.append("\t\t\t%s /* %s */," % (IDS["target"], PROJECT_NAME))
    parts.append("\t\t);")
    parts.append("\t};")
    parts.append("/* End PBXProject section */")
    parts.append("")

    parts.append("/* Begin PBXResourcesBuildPhase section */")
    parts.append("\t%s /* Resources */ = {" % IDS["resources_phase"])
    parts.append("\t\tisa = PBXResourcesBuildPhase;")
    parts.append("\t\tbuildActionMask = 2147483647;")
    parts.append("\t\tfiles = (")
    parts.extend(phase_entries(project.resources, "Resources"))
    parts.append("\t\t);")
    parts.append("\t\trunOnlyForDeploymentPostprocessing = 0;")
    parts.append("\t};")
    parts.append("/* End PBXResourcesBuildPhase section */")
    parts.append("")

    parts.append("/* Begin PBXSourcesBuildPhase section */")
    parts.append("\t%s /* Sources */ = {" % IDS["sources_phase"])
    parts.append("\t\tisa = PBXSourcesBuildPhase;")
    parts.append("\t\tbuildActionMask = 2147483647;")
    parts.append("\t\tfiles = (")
    parts.extend(phase_entries(project.sources, "Sources"))
    parts.append("\t\t);")
    parts.append("\t\trunOnlyForDeploymentPostprocessing = 0;")
    parts.append("\t};")
    parts.append("/* End PBXSourcesBuildPhase section */")
    parts.append("")

    parts.append("/* Begin XCBuildConfiguration section */")
    parts.append(render_settings(IDS["project_debug"], "Debug", project_settings(True)))
    parts.append(render_settings(IDS["project_release"], "Release", project_settings(False)))
    parts.append(render_settings(IDS["target_debug"], "Debug", target_settings(True)))
    parts.append(render_settings(IDS["target_release"], "Release", target_settings(False)))
    parts.append("/* End XCBuildConfiguration section */")
    parts.append("")

    parts.append("/* Begin XCConfigurationList section */")
    parts.append('\t%s /* Build configuration list for PBXProject "%s" */ = {' % (IDS["project_config_list"], PROJECT_NAME))
    parts.append("\t\tisa = XCConfigurationList;")
    parts.append("\t\tbuildConfigurations = (")
    parts.append("\t\t\t%s /* Debug */," % IDS["project_debug"])
    parts.append("\t\t\t%s /* Release */," % IDS["project_release"])
    parts.append("\t\t);")
    parts.append("\t\tdefaultConfigurationIsVisible = 0;")
    parts.append("\t\tdefaultConfigurationName = Release;")
    parts.append("\t};")
    parts.append('\t%s /* Build configuration list for PBXNativeTarget "%s" */ = {' % (IDS["target_config_list"], PROJECT_NAME))
    parts.append("\t\tisa = XCConfigurationList;")
    parts.append("\t\tbuildConfigurations = (")
    parts.append("\t\t\t%s /* Debug */," % IDS["target_debug"])
    parts.append("\t\t\t%s /* Release */," % IDS["target_release"])
    parts.append("\t\t);")
    parts.append("\t\tdefaultConfigurationIsVisible = 0;")
    parts.append("\t\tdefaultConfigurationName = Release;")
    parts.append("\t};")
    parts.append("/* End XCConfigurationList section */")
    parts.append("\t};")
    parts.append("\trootObject = %s /* Project object */;" % IDS["project"])
    parts.append("}")

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    with open(OUTPUT_FILE, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(parts) + "\n")

    scheme_file = os.path.join(OUTPUT_DIR, "xcshareddata", "xcschemes", PROJECT_NAME + ".xcscheme")
    os.makedirs(os.path.dirname(scheme_file), exist_ok=True)
    with open(scheme_file, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(scheme_xml(IDS["target"], PROJECT_NAME))

    print("Wrote %s" % OUTPUT_FILE)
    print("Wrote %s" % scheme_file)
    print("  %d source file(s), %d resource(s)" % (len(project.sources), len(project.resources)))


def scheme_xml(blueprint_id, name):
    reference = (
        "\t\t\t<BuildableReference\n"
        "\t\t\t   BuildableIdentifier = \"primary\"\n"
        "\t\t\t   BlueprintIdentifier = \"%s\"\n"
        "\t\t\t   BuildableName = \"%s.app\"\n"
        "\t\t\t   BlueprintName = \"%s\"\n"
        "\t\t\t   ReferencedContainer = \"container:%s.xcodeproj\">\n"
        "\t\t\t</BuildableReference>"
    ) % (blueprint_id, name, name, name)

    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<Scheme\n'
        '   LastUpgradeVersion = "1600"\n'
        '   version = "1.7">\n'
        '   <BuildAction\n'
        '      parallelizeBuildables = "YES"\n'
        '      buildImplicitDependencies = "YES">\n'
        '      <BuildActionEntries>\n'
        '         <BuildActionEntry\n'
        '            buildForTesting = "YES"\n'
        '            buildForRunning = "YES"\n'
        '            buildForProfiling = "YES"\n'
        '            buildForArchiving = "YES"\n'
        '            buildForAnalyzing = "YES">\n'
        + reference.replace("\t\t\t", "\t\t\t   ") + "\n"
        '         </BuildActionEntry>\n'
        '      </BuildActionEntries>\n'
        '   </BuildAction>\n'
        '   <TestAction\n'
        '      buildConfiguration = "Debug"\n'
        '      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"\n'
        '      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"\n'
        '      shouldUseLaunchSchemeArgsEnv = "YES">\n'
        '   </TestAction>\n'
        '   <LaunchAction\n'
        '      buildConfiguration = "Debug"\n'
        '      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"\n'
        '      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"\n'
        '      launchStyle = "0"\n'
        '      useCustomWorkingDirectory = "NO"\n'
        '      ignoresPersistentStateOnLaunch = "NO"\n'
        '      debugDocumentVersioning = "YES"\n'
        '      debugServiceExtension = "internal"\n'
        '      allowLocationSimulation = "YES">\n'
        '      <BuildableProductRunnable\n'
        '         runnableDebuggingMode = "0">\n'
        + reference.replace("\t\t\t", "\t\t\t   ") + "\n"
        '      </BuildableProductRunnable>\n'
        '   </LaunchAction>\n'
        '   <ProfileAction\n'
        '      buildConfiguration = "Release"\n'
        '      shouldUseLaunchSchemeArgsEnv = "YES"\n'
        '      savedToolIdentifier = ""\n'
        '      useCustomWorkingDirectory = "NO"\n'
        '      debugDocumentVersioning = "YES">\n'
        '      <BuildableProductRunnable\n'
        '         runnableDebuggingMode = "0">\n'
        + reference.replace("\t\t\t", "\t\t\t   ") + "\n"
        '      </BuildableProductRunnable>\n'
        '   </ProfileAction>\n'
        '   <AnalyzeAction\n'
        '      buildConfiguration = "Debug">\n'
        '   </AnalyzeAction>\n'
        '   <ArchiveAction\n'
        '      buildConfiguration = "Release"\n'
        '      revealArchiveInOrganizer = "YES">\n'
        '   </ArchiveAction>\n'
        '</Scheme>\n'
    )


if __name__ == "__main__":
    main()
