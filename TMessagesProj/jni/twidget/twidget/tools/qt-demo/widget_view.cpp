#include "widget_view.h"

#include "qt_renderer.h"

#include <QKeyEvent>
#include <QLineEdit>
#include <QPlainTextEdit>
#include <QMetaObject>
#include <QMouseEvent>
#include <QPainter>
#include <QWheelEvent>

#include <cmath>
#include <unordered_map>

namespace {

int modifiers(Qt::KeyboardModifiers m) {
    // The core's bits: alt, ctrl, meta, shift.
    return (m & Qt::AltModifier ? 1 : 0) | (m & Qt::ControlModifier ? 2 : 0) | (m & Qt::MetaModifier ? 4 : 0) | (m & Qt::ShiftModifier ? 8 : 0);
}

/// The DOM `key` and `code` of a Qt key event; `code` assumes a US layout, as the event has no scan code here.
std::pair<std::string, std::string> dom_key(const QKeyEvent* e) {
    struct Named { int qt; const char* key; const char* code; };
    static const Named named[] = {
        {Qt::Key_Return, "Enter", "Enter"}, {Qt::Key_Enter, "Enter", "NumpadEnter"}, {Qt::Key_Tab, "Tab", "Tab"},
        {Qt::Key_Backspace, "Backspace", "Backspace"}, {Qt::Key_Escape, "Escape", "Escape"}, {Qt::Key_Delete, "Delete", "Delete"},
        {Qt::Key_Left, "ArrowLeft", "ArrowLeft"}, {Qt::Key_Right, "ArrowRight", "ArrowRight"}, {Qt::Key_Up, "ArrowUp", "ArrowUp"},
        {Qt::Key_Down, "ArrowDown", "ArrowDown"}, {Qt::Key_Home, "Home", "Home"}, {Qt::Key_End, "End", "End"},
        {Qt::Key_PageUp, "PageUp", "PageUp"}, {Qt::Key_PageDown, "PageDown", "PageDown"}, {Qt::Key_Space, " ", "Space"},
        {Qt::Key_Shift, "Shift", "ShiftLeft"}, {Qt::Key_Control, "Control", "ControlLeft"}, {Qt::Key_Alt, "Alt", "AltLeft"},
        {Qt::Key_Meta, "Meta", "MetaLeft"},
    };
    for (const Named& n : named)
        if (e->key() == n.qt) return {n.key, n.code};

    std::string key = e->text().toStdString();
    std::string code;
    int k = e->key();
    if (k >= Qt::Key_A && k <= Qt::Key_Z) code = std::string("Key") + char('A' + (k - Qt::Key_A));
    else if (k >= Qt::Key_0 && k <= Qt::Key_9) code = std::string("Digit") + char('0' + (k - Qt::Key_0));
    else if (k >= Qt::Key_F1 && k <= Qt::Key_F12) key = code = "F" + std::to_string(k - Qt::Key_F1 + 1);
    if (key.empty() || (unsigned char)key[0] < 0x20) key = "Unidentified";
    return {key, code};
}

} // namespace

WidgetView::WidgetView(const std::string& html, bool dark, double width, std::shared_ptr<twidget::SandboxResources> resources, QWidget* parent)
    : QWidget(parent), width_(width) {
    setMouseTracking(true);
    animation_ = new QTimer(this);
    animation_->setInterval(16);
    connect(animation_, &QTimer::timeout, this, [this] { update(); });
    setFocusPolicy(Qt::ClickFocus);
    setFixedSize(int(std::lround(width)), 24);

    double dpr = devicePixelRatioF();
    twidget::WidgetHostOptions options;
    options.devicePixelRatio = dpr;
    options.features = {"webAnimations"};

    auto factory = [this, dpr](std::shared_ptr<twidget::SandboxResources>, const std::string& theme, double w) -> std::unique_ptr<twidget::Renderer> {
        auto renderer = std::make_unique<QtRenderer>(theme, w, dpr, [this](std::shared_ptr<QtFrame> frame) {
            {
                std::lock_guard l(frameMutex_);
                frame_ = std::move(frame->document);
                if (frame->layers) layers_ = std::move(*frame->layers);
            }
            QMetaObject::invokeMethod(this, [this] { update(); }, Qt::QueuedConnection);
        });
        renderer->onEditor = [this](std::optional<twlite::Renderer::EditorSpec> spec) {
            QMetaObject::invokeMethod(this, [this, spec] { showEditor(spec); }, Qt::QueuedConnection);
        };
        renderer_ = renderer.get();
        return renderer;
    };

    host_ = std::make_unique<twidget::WidgetHost>(html, dark ? "dark" : "light", width, options, std::move(resources), factory);
    host_->onChange = [this](const twidget::WidgetDiagnostics& d) { changed(d); };
}

WidgetView::~WidgetView() {
    // destroy() joins the host, engine and clock threads, so nothing calls back after it.
    host_->destroy();
}

void WidgetView::start() { host_->start(); }

void WidgetView::setDark(bool dark) { host_->setTheme(dark ? "dark" : "light"); }

void WidgetView::setContentWidth(double width) {
    width_ = width;
    setFixedWidth(int(std::lround(width)));
    // WidgetHost has no width of its own: the renderer reports the new size to it as it would after a batch.
    host_->onHostThread([this, width] { if (renderer_) renderer_->setWidth(width); });
}

void WidgetView::setOnScreen(bool visible) {
    if (visible == onScreen_) return;
    onScreen_ = visible;
    host_->setVisible(visible);
    if (renderer_) renderer_->setVisible(visible);
}

void WidgetView::changed(const twidget::WidgetDiagnostics& d) {
    auto ms = [](const std::optional<double>& v) { return v ? QString::number(*v, 'f', 0) + " ms" : QStringLiteral("–"); };
    QString status;
    if (d.dead) {
        status = QStringLiteral("dead: %1").arg(QString::fromStdString(*d.dead));
    } else {
        status = QStringLiteral("loaded %1 | content %2 | js %3 ms | ops %4 | paints %5 (%6 ms) | build %7 ms")
                     .arg(ms(d.loadedMs), ms(d.firstContentMs))
                     .arg(d.stats.jsMs, 0, 'f', 0)
                     .arg(d.stats.ops)
                     .arg(renderer_ ? renderer_->paints() : 0)
                     .arg(renderer_ ? renderer_->lastPaintMs() : 0, 0, 'f', 1)
                     .arg(renderer_ ? renderer_->lastBuildMs() : 0, 0, 'f', 1);
        if (d.lastError) status += QStringLiteral(" | error: %1").arg(QString::fromStdString(*d.lastError));
    }
    int height = int(std::lround(std::max(d.height, 24.0)));
    QMetaObject::invokeMethod(this, [this, status, height] {
        if (this->height() != height) setFixedHeight(height);
        if (onStatus) onStatus(status);
    }, Qt::QueuedConnection);
}

void WidgetView::paintEvent(QPaintEvent*) {
    QPainter p(this);
    p.setRenderHints(QPainter::Antialiasing | QPainter::SmoothPixmapTransform);
    std::lock_guard l(frameMutex_);
    if (!frame_.isNull()) p.drawImage(QPointF(0, 0), frame_);
    // Each layer where its track has it now, inside its parent's matrix, opacity and clip, the way a
    // compositor plays them. A parent comes before its children.
    struct Placed {
        QTransform world;  // the layer's content space → document coordinates
        qreal opacity = 1;
        bool clipped = false;
        QPainterPath clip;  // document coordinates
    };
    std::unordered_map<int64_t, Placed> placed;
    const QTransform base = p.transform();
    bool running = false;
    double now = twlite::Renderer::clock();
    for (const QtLayer& layer : layers_) {
        double span = 0;
        auto keys = twlite::resume_track(layer.track, now - layer.track.start, span);
        if (keys.empty()) continue;
        Placed up;
        if (layer.parent) {
            auto it = placed.find(layer.parent);
            if (it == placed.end()) continue;
            up = it->second;
        }
        running = running || (keys.size() > 1 && span > 0);
        const twlite::Affine& m = keys.front().matrix;
        Placed self = up;
        if (layer.has_clip) {
            QPainterPath own;
            const twlite::Radii& q = layer.clip_radii;
            own.addRoundedRect(QRectF(layer.clip.x, layer.clip.y, layer.clip.w, layer.clip.h), std::max(q.tlx, q.trx), std::max(q.tly, q.bly));
            own = up.world.map(own);
            self.clip = up.clipped ? up.clip.intersected(own) : own;
            self.clipped = true;
        }
        self.world = QTransform(m.a, m.b, m.c, m.d, m.e, m.f) * up.world;
        self.opacity = up.opacity * keys.front().opacity;
        placed[layer.id] = self;
        p.save();
        p.setTransform(base);
        if (self.clipped) p.setClipPath(self.clip);
        p.setTransform(self.world * base);
        p.setOpacity(self.opacity);
        p.drawImage(QRectF(layer.bounds.x, layer.bounds.y, layer.bounds.w, layer.bounds.h), layer.image);
        p.restore();
    }
    if (running && !animation_->isActive()) animation_->start();
    else if (!running && animation_->isActive()) animation_->stop();
}

void WidgetView::showEditor(std::optional<twlite::Renderer::EditorSpec> spec) {
    if (!spec || (editor_ && editorMultiline_ != spec->multiline)) {
        if (editor_) {
            editor_->removeEventFilter(this);
            editor_->deleteLater();
            editor_ = nullptr;
        }
        if (!spec) return;
    }
    editorGeneration_ = spec->generation;
    editorMultiline_ = spec->multiline;
    if (!editor_) {
        if (spec->multiline) {
            auto* area = new QPlainTextEdit(this);
            area->setFrameShape(QFrame::NoFrame);
            area->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
            area->document()->setDocumentMargin(0);
            connect(area, &QPlainTextEdit::textChanged, this, [this, area] {
                if (renderer_) renderer_->edit(editorGeneration_, area->toPlainText().toStdString());
            });
            editor_ = area;
        } else {
            auto* line = new QLineEdit(this);
            line->setFrame(false);
            line->setTextMargins(0, 0, 0, 0);
            connect(line, &QLineEdit::textEdited, this, [this](const QString& text) {
                if (renderer_) renderer_->edit(editorGeneration_, text.toStdString());
            });
            editor_ = line;
        }
        // The control's own box, border and background stay the document's.
        editor_->setAttribute(Qt::WA_TranslucentBackground);
        editor_->setStyleSheet("background: transparent; border: none;");
        editor_->installEventFilter(this);
    }
    editor_->setGeometry(QRect(int(std::floor(spec->frame.x)), int(std::floor(spec->frame.y)), int(std::ceil(spec->frame.w)), int(std::ceil(spec->frame.h))));
    QFont font;
    QStringList families;
    for (const QString& family : QString::fromStdString(spec->font_family).split(',')) families << family.trimmed().remove('"').remove('\'');
    font.setFamilies(families);
    font.setPixelSize(std::max(1, int(std::lround(spec->font_size))));
    font.setWeight(QFont::Weight(std::clamp(spec->font_weight, 100, 900)));
    font.setItalic(spec->italic);
    editor_->setFont(font);
    QPalette palette = editor_->palette();
    palette.setColor(QPalette::Text, QColor(spec->color.r, spec->color.g, spec->color.b, spec->color.a));
    editor_->setPalette(palette);
    QString text = QString::fromStdString(spec->text);
    if (auto* line = qobject_cast<QLineEdit*>(editor_)) {
        if (line->text() != text) line->setText(text);
        line->setPlaceholderText(QString::fromStdString(spec->placeholder));
        line->setReadOnly(spec->read_only);
        line->setMaxLength(spec->max_length >= 0 ? spec->max_length : 32767);
        line->setAlignment(spec->align == 1 ? Qt::AlignHCenter : spec->align == 2 ? Qt::AlignRight : Qt::AlignLeft);
        if (spec->selection_start >= 0) line->setSelection(spec->selection_start, spec->selection_end - spec->selection_start);
    } else if (auto* area = qobject_cast<QPlainTextEdit*>(editor_)) {
        if (area->toPlainText() != text) {
            QSignalBlocker block(area);
            area->setPlainText(text);
        }
        area->setPlaceholderText(QString::fromStdString(spec->placeholder));
        area->setReadOnly(spec->read_only);
        if (spec->selection_start >= 0) {
            QTextCursor cursor = area->textCursor();
            cursor.setPosition(spec->selection_start);
            cursor.setPosition(spec->selection_end, QTextCursor::KeepAnchor);
            area->setTextCursor(cursor);
        }
    }
    editor_->show();
    editor_->setFocus();
}

bool WidgetView::eventFilter(QObject* watched, QEvent* event) {
    // The editor losing the keyboard ends the editing: change, blur and focusout in the widget.
    if (watched == editor_ && event->type() == QEvent::FocusOut && renderer_) renderer_->endEditing();
    return QWidget::eventFilter(watched, event);
}

void WidgetView::mousePressEvent(QMouseEvent* e) {
    if (renderer_) renderer_->touch(0, e->position().x(), e->position().y(), 1, true, true, modifiers(e->modifiers()));
}

void WidgetView::mouseMoveEvent(QMouseEvent* e) {
    if (!renderer_) return;
    if (e->buttons() != Qt::NoButton) renderer_->touch(1, e->position().x(), e->position().y(), 1, true, true, modifiers(e->modifiers()));
    else renderer_->hover(0, e->position().x(), e->position().y(), modifiers(e->modifiers()));
}

void WidgetView::mouseReleaseEvent(QMouseEvent* e) {
    if (renderer_) renderer_->touch(2, e->position().x(), e->position().y(), 1, true, true, modifiers(e->modifiers()));
}

void WidgetView::leaveEvent(QEvent*) {
    if (renderer_) renderer_->hover(1, 0, 0, 0);
}

void WidgetView::wheelEvent(QWheelEvent* e) {
    if (!renderer_) return;
    // DOM deltas grow downwards and rightwards; a notch is 120 eighths of a degree, about 100 px in browsers.
    QPointF delta = !e->pixelDelta().isNull() ? -QPointF(e->pixelDelta()) : -QPointF(e->angleDelta()) / 120.0 * 100.0;
    renderer_->wheel(e->position().x(), e->position().y(), delta.x(), delta.y(), 0, modifiers(e->modifiers()));
}

void WidgetView::forwardKey(QKeyEvent* e, const char* type) {
    if (!renderer_) return;
    auto [key, code] = dom_key(e);
    renderer_->key(type, key, code, e->isAutoRepeat(), modifiers(e->modifiers()));
}

void WidgetView::keyPressEvent(QKeyEvent* e) { forwardKey(e, "keydown"); }

void WidgetView::keyReleaseEvent(QKeyEvent* e) { forwardKey(e, "keyup"); }
