mod app;
mod comeback;
mod engine;
mod mascot;
mod moves;
mod music;
mod plan;
mod scenery;
mod stage;
mod stats;
mod voice;
mod web;

fn main() {
    console_error_panic_hook::set_once();
    leptos::mount::mount_to_body(app::App);
}
