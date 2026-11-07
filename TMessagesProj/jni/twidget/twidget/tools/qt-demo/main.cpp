// A desktop demo of TwidgetCore on Qt: widget-sandbox's corpus in a scrolling feed of bubbles, each a
// WidgetHost (QuickJS and the validator from widget-sandbox) over the QPainter backend.
//   twidget-qt-demo [corpus folder or .html file]
#include "widget_view.h"

#include <QApplication>
#include <QCheckBox>
#include <QComboBox>
#include <QDir>
#include <QDirIterator>
#include <QFile>
#include <QFileInfo>
#include <QHBoxLayout>
#include <QLabel>
#include <QMainWindow>
#include <QMessageBox>
#include <QPushButton>
#include <QScrollArea>
#include <QScrollBar>
#include <QSignalBlocker>
#include <QSlider>
#include <QTimer>
#include <QVBoxLayout>

#include <vector>

namespace {

class MainWindow : public QMainWindow {
public:
    MainWindow(std::shared_ptr<twidget::SandboxResources> resources, const QString& corpus, const QString& initial)
        : resources_(std::move(resources)), corpus_(corpus) {
        auto* central = new QWidget(this);
        auto* layout = new QVBoxLayout(central);

        auto* bar = new QHBoxLayout();
        folders_ = new QComboBox();
        QDirIterator it(corpus_, QDir::Dirs | QDir::NoDotAndDotDot, QDirIterator::Subdirectories);
        QStringList found;
        while (it.hasNext()) {
            QString dir = it.next();
            if (!QDir(dir).entryList({"*.html"}, QDir::Files).isEmpty()) found.push_back(QDir(corpus_).relativeFilePath(dir));
        }
        found.sort();
        folders_->addItems(found);
        bar->addWidget(new QLabel("Corpus"));
        bar->addWidget(folders_, 1);
        dark_ = new QCheckBox("Dark");
        bar->addWidget(dark_);
        bar->addWidget(new QLabel("Width"));
        width_ = new QSlider(Qt::Horizontal);
        width_->setRange(240, 640);
        width_->setValue(360);
        width_->setFixedWidth(160);
        bar->addWidget(width_);
        widthLabel_ = new QLabel("360");
        bar->addWidget(widthLabel_);
        auto* reload = new QPushButton("Reload");
        bar->addWidget(reload);
        layout->addLayout(bar);

        scroll_ = new QScrollArea();
        scroll_->setWidgetResizable(true);
        layout->addWidget(scroll_, 1);
        setCentralWidget(central);
        resize(820, 960);

        connect(folders_, &QComboBox::currentTextChanged, this, [this] { load(QDir(corpus_).filePath(folders_->currentText())); });
        connect(reload, &QPushButton::clicked, this, [this] { load(current_); });
        connect(dark_, &QCheckBox::toggled, this, [this](bool dark) {
            applyTheme();
            for (WidgetView* v : views_) v->setDark(dark);
        });
        connect(width_, &QSlider::valueChanged, this, [this](int w) {
            widthLabel_->setText(QString::number(w));
            for (WidgetView* v : views_) v->setContentWidth(w);
        });
        connect(scroll_->verticalScrollBar(), &QScrollBar::valueChanged, this, [this] { updateVisibility(); });

        if (!initial.isEmpty()) {
            QSignalBlocker quiet(folders_);
            folders_->setCurrentIndex(folders_->findText(QDir(corpus_).relativeFilePath(initial)));
            load(initial);
        } else {
            int common = folders_->findText("common/cards");
            if (common >= 0) folders_->setCurrentIndex(common);
            load(QDir(corpus_).filePath(folders_->currentText()));
        }
    }

    ~MainWindow() override { clear(); }

private:
    void clear() {
        // Each view joins its widget's threads as it goes.
        for (WidgetView* v : views_) delete v;
        views_.clear();
    }

    void load(const QString& path) {
        current_ = path;
        clear();
        QStringList files;
        if (QFileInfo(path).isFile()) {
            files.push_back(path);
        } else {
            for (const QString& name : QDir(path).entryList({"*.html"}, QDir::Files, QDir::Name)) files.push_back(QDir(path).filePath(name));
        }

        auto* feed = new QWidget();
        auto* column = new QVBoxLayout(feed);
        column->setSpacing(14);
        for (const QString& file : files) {
            QFile f(file);
            if (!f.open(QIODevice::ReadOnly)) continue;
            std::string html = f.readAll().toStdString();

            auto* card = new QWidget();
            auto* stack = new QVBoxLayout(card);
            stack->setContentsMargins(0, 0, 0, 0);
            stack->setSpacing(2);
            stack->addWidget(new QLabel(QStringLiteral("<b>%1</b>").arg(QFileInfo(file).fileName())));
            auto* status = new QLabel();
            status->setStyleSheet("color: gray; font-size: 11px");
            WidgetView* view = nullptr;
            try {
                view = new WidgetView(html, dark_->isChecked(), width_->value(), resources_);
            } catch (const std::exception& e) {
                status->setText(QStringLiteral("failed: %1").arg(e.what()));
            }
            if (view) {
                view->onStatus = [status](const QString& s) { status->setText(s); };
                stack->addWidget(view, 0, Qt::AlignLeft);
                views_.push_back(view);
            }
            stack->addWidget(status);
            column->addWidget(card);
        }
        column->addStretch(1);
        scroll_->setWidget(feed);
        applyTheme();
        for (WidgetView* v : views_) v->start();
        QTimer::singleShot(0, this, [this] { updateVisibility(); });
        setWindowTitle(QStringLiteral("twidget on Qt: %1 (%2 widgets)").arg(QDir(corpus_).relativeFilePath(path)).arg(views_.size()));
    }

    void applyTheme() {
        bool dark = dark_->isChecked();
        // The bubble colours of the chat, under widgets whose own background is transparent.
        scroll_->widget()->setStyleSheet(dark ? "background: #212d3b; color: #e8e8e8" : "background: #effdde; color: #000");
    }

    // Off-screen widgets pause: no animation frames, timers at 1 Hz.
    void updateVisibility() {
        QRect viewport(QPoint(0, 0), scroll_->viewport()->size());
        for (WidgetView* v : views_) {
            QRect r(v->mapTo(scroll_->viewport(), QPoint(0, 0)), v->size());
            v->setOnScreen(viewport.intersects(r));
        }
    }

    std::shared_ptr<twidget::SandboxResources> resources_;
    QString corpus_;
    QString current_;
    QComboBox* folders_;
    QCheckBox* dark_;
    QSlider* width_;
    QLabel* widthLabel_;
    QScrollArea* scroll_;
    std::vector<WidgetView*> views_;
};

} // namespace

int main(int argc, char** argv) {
    QApplication app(argc, argv);

    std::shared_ptr<twidget::SandboxResources> resources;
    try {
        resources = twidget::SandboxResources::fromDirectory(TW_WIDGET_JS);
    } catch (const std::exception& e) {
        QMessageBox::critical(nullptr, "twidget", QStringLiteral("widget-sandbox's WidgetJS bundles: %1").arg(e.what()));
        return 1;
    }

    QString initial = argc > 1 ? QString::fromLocal8Bit(argv[1]) : QString();
    MainWindow window(resources, QString(TW_CORPUS), initial.isEmpty() ? QString() : QFileInfo(initial).absoluteFilePath());
    window.show();
    return app.exec();
}
