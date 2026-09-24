# QA helper: report the cell renderer of every visible tree and table.
#
# Run it from the Designer's Script Console (Tools -> Script Console), which
# executes in the Designer's own JVM:
#
#     OUTPATH='/tmp/ddm-qa/stock.txt'; execfile('.../ops/qa-renderer-probe.py')
#
# Take one sample before a dark/light cycle and one after, then diff them. A
# restore that loses a renderer shows up as a changed class name; nothing else
# in the module reports this (the debug log only counts the components it
# wraps, and the inspector reports colours and borders, not renderers).
#
# Set CONTROL = True to run a plain updateComponentTreeUI on the EDT first,
# with the module uninvolved. That is the baseline a cycle should be compared
# against: a bare tree update is NOT a no-op even Synthetica->Synthetica, so
# some renderer changes are Swing's rather than ours. See QA-CHECKLIST.md,
# "The renderer probe".
#
# Requires OUTPATH to be defined by the caller. CONTROL defaults to False.
#
# The notes block above the ---- separator carries the window count and any
# component the walk could not describe. A couple of "describe:" tracebacks are
# normal: Jython cannot stringify some Designer classes (a listener it turns
# into a proxy), and the walk keeps going past them.

from java.awt import Window
from java.lang import Object as JObject, Runnable
from javax.swing import JTable, JTree, SwingUtilities
from javax.swing.plaf.basic import BasicTreeUI
import traceback

try:
    CONTROL
except NameError:
    CONTROL = False

lines = []
notes = []


def cname(o):
    # o.getClass().getName() resolves to the wrong overload for some Swing
    # objects under Jython; str(getClass()) is stable.
    return "None" if o is None else str(o.getClass()).replace("class ", "")


def created_flag(tree):
    """Whether this tree's renderer was created by the look and feel.

    The flag the #42 fixes turn on: BasicTreeUI drops the renderer it CREATED
    on uninstall, and any setCellRenderer call clears it.
    """
    try:
        ui = tree.getUI()
        if not isinstance(ui, BasicTreeUI):
            return "ui=" + cname(ui)
        f = BasicTreeUI.getDeclaredField("createdRenderer")
        f.setAccessible(True)
        return "createdRenderer=" + str(f.get(ui))
    except:
        return "createdRenderer=UNREADABLE"


def walk(c):
    try:
        if isinstance(c, JTree):
            lines.append("TREE  %s | renderer=%s | rowHeight=%s | %s"
                         % (cname(c), cname(c.getCellRenderer()),
                            c.getRowHeight(), created_flag(c)))
        elif isinstance(c, JTable):
            lines.append("TABLE %s | default=%s | rowHeight=%s | rows=%s"
                         % (cname(c), cname(c.getDefaultRenderer(JObject)),
                            c.getRowHeight(), c.getRowCount()))
    except:
        notes.append("describe: " + traceback.format_exc())
    try:
        kids = c.getComponents()
    except:
        kids = []
    for child in kids:
        walk(child)


class Refresh(Runnable):
    def run(self):
        for w in Window.getWindows():
            try:
                if w.isShowing():
                    SwingUtilities.updateComponentTreeUI(w)
            except:
                pass


if CONTROL:
    SwingUtilities.invokeAndWait(Refresh())
    notes.append("control: plain updateComponentTreeUI ran on the EDT")

windows = Window.getWindows()
notes.append("windows=%d" % len(windows))
for w in windows:
    try:
        if w.isShowing():
            walk(w)
    except:
        notes.append("window: " + traceback.format_exc())

lines.sort()
handle = open(OUTPATH, "w")
handle.write("\n".join(notes) + "\n----\n" + "\n".join(lines) + "\n")
handle.close()
print "probe: %d components, %d note(s) -> %s" % (len(lines), len(notes), OUTPATH)
